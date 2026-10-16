package com.newspush.app.util

import java.security.MessageDigest

fun md5(input: String): String =
    MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
