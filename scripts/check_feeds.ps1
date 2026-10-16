$ErrorActionPreference = "Continue"
$env:HTTP_PROXY = ""
$env:HTTPS_PROXY = ""
$env:ALL_PROXY = ""
$env:GIT_HTTP_PROXY = ""
$env:GIT_HTTPS_PROXY = ""
$env:NO_PROXY = "*"

$targets = @(
    @{ k = "people-politics";  u = "http://www.people.com.cn/rss/politics.xml" },
    @{ k = "bbc-zh";           u = "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml" },
    @{ k = "nyt-zh";           u = "https://cn.nytimes.com/rss/" },
    @{ k = "36kr";             u = "https://36kr.com/feed" },
    @{ k = "36kr-newsflash";   u = "https://36kr.com/feed-newsflash" },
    @{ k = "sspai";            u = "https://sspai.com/feed" },
    @{ k = "huxiu";            u = "https://www.huxiu.com/rss/0.xml" },
    @{ k = "solidot";          u = "https://www.solidot.org/index.rss" },
    @{ k = "ithome";           u = "https://www.ithome.com/rss/" },
    @{ k = "chinanews-scroll"; u = "https://www.chinanews.com.cn/rss/scroll-news.xml" },
    @{ k = "chinanews-import"; u = "https://www.chinanews.com.cn/rss/importnews.xml" },
    @{ k = "sina-tech";        u = "http://rss.sina.com.cn/tech/rollnews.xml" },
    @{ k = "cnblogs-home";     u = "https://feed.cnblogs.com/blog/sitehome/rss" },
    @{ k = "cnblogs-picked";   u = "https://feed.cnblogs.com/blog/picked/rss" },
    @{ k = "oschina-news";     u = "https://www.oschina.net/news/rss" },
    @{ k = "toutiao-io";       u = "https://toutiao.io/rss" },
    @{ k = "segmentfault";     u = "https://segmentfault.com/feeds/blogs" },
    @{ k = "ruanyifeng";       u = "https://www.ruanyifeng.com/blog/atom.xml" },
    @{ k = "meituan-tech";     u = "https://tech.meituan.com/feed/" },
    @{ k = "infoq-cn";         u = "https://www.infoq.cn/feed" },
    @{ k = "linux-cn";         u = "https://linux.cn/feed" },
    @{ k = "v2ex";             u = "https://www.v2ex.com/index.xml" },
    @{ k = "juejin";           u = "https://juejin.cn/rss" },
    @{ k = "guokr";            u = "https://www.guokr.com/rss/" },
    @{ k = "hackernews";       u = "https://hnrss.org/frontpage" },
    @{ k = "devto";            u = "https://dev.to/feed" },
    @{ k = "wallstreetcn";     u = "https://wallstreetcn.com/rss" }
)

Write-Host ("{0,-18} {1,-5} {2,-9} {3,-6} {4,-22} {5}" -f "source", "code", "bytes", "items", "newest", "first title")
Write-Host ("-" * 110)

foreach ($t in $targets) {
    $tmp = Join-Path $env:TEMP ("feed-check-" + [System.IO.Path]::GetRandomFileName() + ".xml")
    $code = & curl.exe --noproxy "*" -s -L --compressed --max-time 25 -A "NewsPush/1.0 (RSS reader)" -o $tmp -w "%{http_code}" $t.u
    $bytes = 0
    $items = 0
    $newest = "-"
    $firstTitle = "-"

    if (Test-Path -LiteralPath $tmp) {
        $bytes = (Get-Item -LiteralPath $tmp).Length
        if ($bytes -gt 0) {
            $raw = [System.IO.File]::ReadAllText($tmp, [System.Text.Encoding]::UTF8)
            $items = ([regex]::Matches($raw, "<item[ >]")).Count + ([regex]::Matches($raw, "<entry[ >]")).Count

            $max = $null
            foreach ($m in [regex]::Matches($raw, "<(?:pubDate|published|updated|dc:date)>([^<]+)<")) {
                $parsed = [datetime]::MinValue
                $ok = [datetime]::TryParse(
                    $m.Groups[1].Value.Trim(),
                    [System.Globalization.CultureInfo]::InvariantCulture,
                    [System.Globalization.DateTimeStyles]::None,
                    [ref]$parsed)
                if ($ok -and (-not $max -or $parsed -gt $max)) { $max = $parsed }
            }
            if ($max) { $newest = $max.ToString("yyyy-MM-dd") }

            $tm = [regex]::Match($raw, "<(?:item|entry)[^>]*>\s*<title[^>]*>(.*?)</title>", "Singleline")
            if ($tm.Success) {
                $clean = ($tm.Groups[1].Value -replace "<[^>]+>", "").Trim()
                $clean = ($clean -replace "[^\x20-\x7E]", "")
                if ($clean.Length -gt 40) { $clean = $clean.Substring(0, 40) }
                if ($clean) { $firstTitle = $clean }
            }
        }
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }

    Write-Host ("{0,-18} {1,-5} {2,-9} {3,-6} {4,-22} {5}" -f $t.k, $code, $bytes, $items, $newest, $firstTitle)
}
