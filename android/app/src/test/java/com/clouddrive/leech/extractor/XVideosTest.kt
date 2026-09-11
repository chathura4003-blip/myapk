package com.clouddrive.leech.extractor

import com.clouddrive.leech.extractor.providers.adult.XVideosScraper
import org.junit.Test

class XVideosTest {
    @Test
    fun testXVideosTrending() {
        val scraper = XVideosScraper()
        println("Testing page 1 (trending)...")
        val p1 = scraper.search("", 1)
        println("Page 1 results count: ${p1.size}")
        p1.take(5).forEachIndexed { i, item ->
            println("  #$i: title=${item.title}, link=${item.link}, thumb=${item.thumbnail}, dur=${item.duration}, views=${item.views}")
        }

        println("Testing page 2...")
        val p2 = scraper.search("", 2)
        println("Page 2 results count: ${p2.size}")

        println("Testing search 'milf'...")
        val searchResults = scraper.search("milf", 1)
        println("Search results count: ${searchResults.size}")

        if (p1.isNotEmpty()) {
            val firstVideo = p1[0]
            println("Testing stream resolution for: ${firstVideo.link}")
            val res = scraper.resolve(firstVideo.link)
            println("Resolved StreamResult:")
            println("  title: ${res?.title}")
            println("  streamUrl: ${res?.streamUrl}")
            println("  type: ${res?.type}")
            println("  qualities: ${res?.qualities?.size}")
            res?.qualities?.forEach { q ->
                println("    quality: ${q.quality} (${q.label}) -> ${q.streamUrl}")
            }
        }
    }
}
