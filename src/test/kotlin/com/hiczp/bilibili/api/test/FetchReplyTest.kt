package com.hiczp.bilibili.api.test

import com.hiczp.bilibili.api.BilibiliClient
import com.hiczp.bilibili.api.main.model.ChildReply
import com.hiczp.bilibili.api.main.model.Reply
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FetchReplyTest {
    @Test
    fun fetchReply() {
        runBlocking {
            noLoginBilibiliClient.mainAPI.reply(oid = 44154463).await()
        }
    }

    @Test
    fun fetchChildReply() {
        runBlocking {
            noLoginBilibiliClient.mainAPI.childReply(oid = 16622855, root = 1405602348).await()
        }
    }

    //打印一个视频下全部的评论
    @Test
    fun printAllReplies() {
        val start = System.currentTimeMillis()

        val aid = 150998L
        val bilibiliClient = BilibiliClient()

        var total: Int? = null
        var next: Long = 0
        runBlocking {
            //pageSize=1 来获得评论总楼层数量
            val reply = bilibiliClient.mainAPI.reply(oid = aid, pageSize = 1).await()
            //得到评论总数(根评论+子评论)
            total = reply.data.cursor.allCount
            //最后一楼
            next = reply.data.cursor.next
        }

        //如果没有评论则不做进一步操作
        if (total == null) {
            println("<NoReply>")
        } else {
            val results = ArrayList<Deferred<List<Pair<Reply.Data.Reply, Deferred<ChildReply>?>>>>()
            //访问每个页
            //如果根评论数量刚好能被 50 整除, 那么最后一次访问时的 next 为 1, 这会导致 replies 为 null
            //因此 downTo 2
            for (i in next + 1 downTo 2 step 50) {
                GlobalScope.async {
                    val replies = bilibiliClient.mainAPI.reply(oid = aid, next = i, pageSize = 50).await().data.replies
                    //获取该页的评论(复数)的子评论
                    replies!!.map {
                        it to if (it.rcount == 0) {
                            null
                        } else {
                            bilibiliClient.mainAPI.childReply(oid = aid, root = it.rpid, size = Int.MAX_VALUE)
                        }
                    }
                }.let {
                    results.add(it)
                }
            }
            //join
            runBlocking {
                results.forEach { deferred ->
                    deferred.await().forEach { (reply, childReplyResponse) ->
                        println("#${reply.floor} [${reply.member.uname}] ${reply.content.message}")
                        childReplyResponse?.await()?.data?.root?.replies?.forEach {
                            println("└──#${it.floor} [${it.member.uname}] ${it.content.message}")
                        }
                    }
                }
            }
        }

        val end = System.currentTimeMillis()
        println("Done in ${end - start} ms")
    }
}
