package com.miaokatze.gtsr.tests;

import java.util.concurrent.CountDownLatch;
import com.miaokatze.gtsr.common.network.BoundedRequestQueue;

/** Pure-JDK admission invariants, including concurrent Netty-style producers. */
public final class BoundedRequestQueueRegression {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        BoundedRequestQueue<Integer> queue=new BoundedRequestQueue<>();Object owner=new Object();
        for(int n=0;n<16;n++)check(queue.offer(owner,n),"admit per-connection capacity");
        check(!queue.offer(owner,99),"per-connection bound");
        for(int n=0;n<16;n++)check(queue.poll()==n,"FIFO");
        check(queue.poll()==null,"empty poll");check(queue.offer(owner,1),"poll restores owner capacity");queue.clear();
        check(queue.offer(owner,2),"clear restores owner capacity");queue.clear();
        check(queue.offer(new String("same"),1)&&queue.offer(new String("same"),2),"owners use identity not equality");queue.clear();
        CountDownLatch ready=new CountDownLatch(32),start=new CountDownLatch(1);Thread[] threads=new Thread[32];
        for(int n=0;n<threads.length;n++){final Object connection=new Object();threads[n]=new Thread(()->{ready.countDown();try{start.await();}catch(InterruptedException e){throw new AssertionError(e);}for(int i=0;i<1000;i++)queue.offer(connection,i);});threads[n].start();}
        ready.await();start.countDown();for(Thread t:threads)t.join();check(queue.size()==256,"concurrent global bound");
        int drained=0;while(queue.poll()!=null)drained++;check(drained==256,"concurrent accounting matches FIFO size");
        for(int n=0;n<16;n++)check(queue.offer(owner,n),"no owner-accounting leak after concurrent drain");
        System.out.println("PASS bounded queue checks="+checks);
    }
}
