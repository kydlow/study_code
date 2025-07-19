package com.demo.handlerstudy

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import com.demo.handlerstudy.databinding.LayoutMainBinding


class MainActivity : Activity(),OnClickListener {
    companion object{
        private const val MESSAGE_CODE = 1
    }
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var binding: LayoutMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createHandler()
        binding.btnSendSync.setOnClickListener(this)
        binding.btnSendAsync.setOnClickListener(this)
        binding.btnAddSyncBarrier.setOnClickListener(this)
        binding.btnRemoSyncBarrier.setOnClickListener(this)
    }


    private fun createHandler() {
        handlerThread = HandlerThread("ellery")
        handlerThread.start()
        handler = MyHandler(handlerThread.looper)
    }


    private inner class MyHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == MESSAGE_CODE) {
                val content = msg.obj as String
                Log.d("ellery", "handleMessage: receive $content")
                printMessageQueue()
            }
        }
    }

    private var syncMessageCount = 0
    private var asyncMessageCount = 0
    private val tokenList = arrayListOf<Int>()

    // 发送 message 同步或异步
    private fun sendMessage(isAsync:Boolean){
        syncMessageCount++
        val message = Message.obtain()
        val content = if(isAsync) "async message $syncMessageCount" else "sync message $syncMessageCount"
        message.obj = content
        message.what = MESSAGE_CODE
        message.isAsynchronous = isAsync
        //延迟多少秒发送
        handler.sendMessageDelayed(message,1000L * (syncMessageCount + asyncMessageCount))
        printMessageQueue()
    }

    //发送同步屏障信息
    private fun sendSyncBarrierMessage(){
        try{
            val messageQueue = handler.looper.queue
            //反射调用postSyncBarrier方法测试,带when参数的已经不能用了，直接用
            //  public int postSyncBarrier() {
            //        return postSyncBarrier(SystemClock.uptimeMillis());
            //    }
            val postSyncBarrierMethod = MessageQueue::class.java.getDeclaredMethod("postSyncBarrier")
            postSyncBarrierMethod.isAccessible = true

            val tokenObj = postSyncBarrierMethod.invoke(messageQueue)
            if (tokenObj is Int) {
                val token = tokenObj as Int
                tokenList.add(token)
                Log.d("ellery", "sendSyncBarrierMessage: token=$token")
            }
            printMessageQueue()
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    private fun removeSyncBarrierMessage() {
        try {
            val messageQueue = handler.looper.queue
            //反射调用removeSyncBarrier方法测试
            val removeSyncBarrierMethod = MessageQueue::class.java.getDeclaredMethod("removeSyncBarrier", Int::class.java)
            removeSyncBarrierMethod.isAccessible = true
            val token = tokenList.removeLastOrNull()
            Log.d("ellery", "removeSyncBarrierMessage: token=$token")
            if (token != null) {
                removeSyncBarrierMethod.invoke(messageQueue, token)
                printMessageQueue()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun printMessageQueue() {
        try {
            Log.d("ellery", "printMessageQueue: ---------------------start")
            val messageQueue = handler.looper.queue
            val mMessagesField = MessageQueue::class.java.getDeclaredField("mMessages")
            mMessagesField.isAccessible = true
            val mMessages = mMessagesField.get(messageQueue) as? Message
            var message: Message? = mMessages
            if (mMessages == null) {
                Log.d("ellery", "printMessageQueue: no message")
                binding.tvMessage.post { binding.tvMessage.text = "no message" }
                syncMessageCount = 0
                asyncMessageCount = 0
                return
            }
            val sb = StringBuilder()
            while (message != null) {
                val printMessage = printMessage(message)
                sb.append(printMessage).append("\n")
                Log.d("ellery", "printMessageQueue: $printMessage")
                val nextField = Message::class.java.getDeclaredField("next")
                nextField.isAccessible = true
                message = nextField.get(message) as? Message
            }
            binding.tvMessage.post { binding.tvMessage.text = sb.toString() }

            Log.d("ellery", "printMessageQueue: ---------------------end")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun printMessage(message: Message): String {
        val b = StringBuilder()
        b.append("{ ")

        if (message.target != null) {
            if (message.obj != null) {
                b.append(" obj=")
                b.append(message.obj)
            }
            b.append(" target=")
            b.append(message.target.javaClass.simpleName)
        } else {
            b.append(" sync barrier message ${message.arg1}")
        }

        b.append(" }")
        return b.toString()
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quit()
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btnSendSync->{
                sendMessage(isAsync = false)
            }

            R.id.btnSendAsync->{
                sendMessage(isAsync = true)
            }

            R.id.btnAddSyncBarrier->{
                sendSyncBarrierMessage()
            }

            R.id.btnRemoSyncBarrier->{
                removeSyncBarrierMessage()
            }
        }
    }
}
