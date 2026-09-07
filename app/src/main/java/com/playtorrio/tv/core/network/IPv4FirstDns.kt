package com.playtorrio.tv.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Custom DNS that reorders resolved addresses to place IPv4 (Inet4Address)
 * before IPv6 (Inet6Address). This avoids 60s timeout delays on networks
 * with broken IPv6 routing (issue #651).
 */
class IPv4FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = delegate.lookup(hostname)
            addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
        } catch (e: Exception) {
            if (hostname.contains("subtitlecat.com", ignoreCase = true)) {
                listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(104.toByte(), 21.toByte(), 71.toByte(), 250.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(172.toByte(), 67.toByte(), 172.toByte(), 156.toByte()))
                )
            } else {
                throw e
            }
        }
    }
}
