import asyncio
import hashlib
import html
import re
import time
from typing import Any, Dict, List, Optional
from urllib.parse import quote

import httpx

from .config import settings


MIXIN_TABLE = [
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
    27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
    37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
    22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
]

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
SPI_URL = "https://api.bilibili.com/x/frontend/finger/spi"
NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
SPACE_URL = "https://api.bilibili.com/x/space/wbi/arc/search"
SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
TAG_RE = re.compile(r"<[^>]+>")


class BiliError(Exception):
    def __init__(self, message: str, code: Optional[int] = None) -> None:
        super().__init__(message)
        self.code = code


def _clean(text: Any) -> str:
    return html.unescape(TAG_RE.sub("", str(text or ""))).strip()


def _normalize(
    bvid: Any,
    title: Any,
    description: Any,
    pic: Any,
    published_at: Any,
    play: Any = 0,
    like_count: Any = 0,
) -> Optional[Dict[str, Any]]:
    bvid_text = str(bvid or "").strip()
    title_text = _clean(title)
    if not bvid_text or not title_text:
        return None
    desc_text = _clean(description) or title_text
    cover = str(pic or "").strip()
    if cover.startswith("//"):
        cover = "https:" + cover
    try:
        ts = int(published_at or 0)
    except (TypeError, ValueError):
        ts = 0
    if ts <= 0:
        ts = int(time.time())
    return {
        "post_id": bvid_text,
        "title": title_text,
        "description": desc_text[:2000],
        "url": "https://www.bilibili.com/video/" + bvid_text,
        "cover_url": cover,
        "published_at": ts,
        "play": int(play or 0),
        "like_count": int(like_count or 0),
    }


class BiliClient:
    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            timeout=httpx.Timeout(20.0),
            headers={"User-Agent": USER_AGENT},
            follow_redirects=True,
        )
        self._session_lock = asyncio.Lock()
        self._throttle_lock = asyncio.Lock()
        self._last_request = 0.0
        self._cookie = ""
        self._mixin = ""

    async def close(self) -> None:
        await self._client.aclose()

    async def _throttle(self) -> None:
        async with self._throttle_lock:
            wait = self._last_request + settings.bili_min_interval_seconds - time.monotonic()
            if wait > 0:
                await asyncio.sleep(wait)
            self._last_request = time.monotonic()

    async def _get_json(
        self,
        url: str,
        params: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        await self._throttle()
        try:
            response = await self._client.get(url, params=params, headers=headers)
        except httpx.HTTPError as exc:
            raise BiliError("network error: " + str(exc)) from exc
        if response.status_code == 412:
            raise BiliError("http 412 (risk control)", 412)
        if response.status_code >= 400:
            raise BiliError("http " + str(response.status_code), response.status_code)
        try:
            return response.json()
        except ValueError as exc:
            raise BiliError("invalid json response") from exc

    async def _ensure_session(self) -> None:
        if self._mixin:
            return
        async with self._session_lock:
            if self._mixin:
                return
            spi = await self._get_json(SPI_URL)
            data = spi.get("data") or {}
            b3 = data.get("b_3")
            b4 = data.get("b_4")
            if not b3 or not b4:
                raise BiliError("cannot obtain buvid cookies")
            cookie = "buvid3=" + b3 + "; buvid4=" + b4 + "; b_nut=" + str(int(time.time()))
            if settings.bili_sessdata:
                cookie += "; SESSDATA=" + settings.bili_sessdata
            nav = await self._get_json(NAV_URL, headers={"Cookie": cookie})
            wbi = ((nav.get("data") or {}).get("wbi_img") or {})
            img_url = wbi.get("img_url")
            sub_url = wbi.get("sub_url")
            if not img_url or not sub_url:
                raise BiliError("cannot obtain wbi keys")
            img_key = str(img_url).rsplit("/", 1)[-1].split(".")[0]
            sub_key = str(sub_url).rsplit("/", 1)[-1].split(".")[0]
            raw_key = img_key + sub_key
            self._mixin = "".join(raw_key[i] for i in MIXIN_TABLE)[:32]
            self._cookie = cookie

    def _sign(self, params: Dict[str, Any]) -> str:
        values = {str(k): str(v) for k, v in params.items()}
        values["wts"] = str(int(time.time()))
        parts = []
        for key in sorted(values):
            value = re.sub(r"[!'()*]", "", values[key])
            parts.append(quote(key, safe="") + "=" + quote(value, safe=""))
        query = "&".join(parts)
        digest = hashlib.md5((query + self._mixin).encode("utf-8")).hexdigest()
        return query + "&w_rid=" + digest

    async def fetch_account(self, uid: str, name: str) -> List[Dict[str, Any]]:
        await self._ensure_session()
        errors = []

        try:
            items = await self._fetch_space(uid)
            if items:
                return items
            errors.append("space returned 0 items")
        except BiliError as exc:
            errors.append("space: " + str(exc))

        try:
            items = await self._search_videos(uid, name)
            if items:
                return items
            errors.append("search returned 0 items")
        except BiliError as exc:
            errors.append("search: " + str(exc))

        raise BiliError("; ".join(errors))

    async def _fetch_space(self, uid: str) -> List[Dict[str, Any]]:
        params = {
            "mid": uid,
            "ps": 5,
            "pn": 1,
            "order": "pubdate",
            "platform": "web",
            "web_location": "1550101",
            "dm_img_list": "[]",
            "dm_img_str": "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
            "dm_cover_img_str": (
                "QU5HTEUgKEludGVsLCBJbnRlbChSKSBVSEQgR3JhcGhpY3MgNjMwICgweDAwMDAzRTlCKSBEaXJlY3QzRDEx"
                "IHZzXzVfMCBwc181XzApLCBvciBzaW1pbGFy"
            ),
            "dm_img_inter": '{"ds":[],"wh":[0,0,0],"of":[0,0,0]}',
        }
        query = self._sign(params)
        data = await self._get_json(
            SPACE_URL + "?" + query,
            headers={
                "Cookie": self._cookie,
                "Referer": "https://space.bilibili.com/" + uid + "/video",
            },
        )
        if data.get("code") != 0:
            raise BiliError(
                "space code " + str(data.get("code")) + ": " + str(data.get("message")),
                data.get("code"),
            )
        vlist = (((data.get("data") or {}).get("list") or {}).get("vlist")) or []
        items = []
        for video in vlist:
            item = _normalize(
                video.get("bvid"),
                video.get("title"),
                video.get("description"),
                video.get("pic"),
                video.get("created"),
                video.get("play"),
                video.get("like"),
            )
            if item:
                items.append(item)
        return items

    async def _search_videos(self, uid: str, name: str) -> List[Dict[str, Any]]:
        items = []
        seen = set()
        for page in (1, 2):
            params = {
                "search_type": "video",
                "keyword": name,
                "order": "pubdate",
                "page": page,
                "page_size": 20,
            }
            query = self._sign(params)
            data = await self._get_json(
                SEARCH_URL + "?" + query,
                headers={
                    "Cookie": self._cookie,
                    "Referer": "https://search.bilibili.com/",
                },
            )
            if data.get("code") != 0:
                raise BiliError(
                    "search code " + str(data.get("code")) + ": " + str(data.get("message")),
                    data.get("code"),
                )
            results = (data.get("data") or {}).get("result") or []
            for video in results:
                if str(video.get("mid")) != str(uid):
                    continue
                item = _normalize(
                    video.get("bvid"),
                    video.get("title"),
                    video.get("description"),
                    video.get("pic"),
                    video.get("pubdate"),
                    video.get("play"),
                    video.get("favorites"),
                )
                if item and item["post_id"] not in seen:
                    seen.add(item["post_id"])
                    items.append(item)
            if len(items) >= 5:
                break
        items.sort(key=lambda item: item["published_at"], reverse=True)
        return items[:5]
