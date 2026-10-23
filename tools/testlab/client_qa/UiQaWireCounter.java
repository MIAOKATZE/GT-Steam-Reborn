package audit;

import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Counts actual custom payloads at the owned client's native NetworkManager pipeline. */
final class UiQaWireCounter extends ChannelDuplexHandler {
    final Mui2ClientQa owner;
    final Map<String,AtomicLong> values=new ConcurrentHashMap<String,AtomicLong>();
    volatile JsonObject lastMuiOpen;
    private UiQaWireCounter(Mui2ClientQa owner){this.owner=owner;}
    static UiQaWireCounter install(Mui2ClientQa owner,Object player)throws Exception {
        Object handler=Mui2ClientQa.field(player,"sendQueue|field_71174_a");
        Object network=Mui2ClientQa.field(handler,"netManager|field_147302_e");
        Channel channel=(Channel)Mui2ClientQa.field(network,"channel|field_150746_k");
        UiQaWireCounter counter=new UiQaWireCounter(owner);
        if(!channel.pipeline().names().contains("packet_handler"))throw new IllegalStateException("Native packet_handler absent");
        channel.pipeline().addBefore("packet_handler","gtsr_owned_uiqa",counter);
        owner.receipt.addProperty("packetMeasurementScope","Actual NetworkManager custom-payload messages/content bytes, split by client phase and direction; integrated LocalChannel has no TCP/compression wire-byte claim; background channels retained separately");
        return counter;
    }
    void count(String direction,Object message){
        try {
            String type=message.getClass().getName(),channel=null;Object payload=null;
            if(type.endsWith(".FMLProxyPacket")){channel=String.valueOf(Mui2ClientQa.call(message,"channel"));payload=Mui2ClientQa.call(message,"payload");}
            else if(type.endsWith(".C17PacketCustomPayload")){channel=String.valueOf(Mui2ClientQa.call(message,"getChannelName|func_149559_c"));payload=Mui2ClientQa.call(message,"getBufferData|func_149558_e");}
            else if(type.endsWith(".S3FPacketCustomPayload")){channel=String.valueOf(Mui2ClientQa.call(message,"getChannelName|func_149169_c"));payload=Mui2ClientQa.call(message,"getBufferData|func_149168_d");}
            if(channel==null)return;
            if(direction.equals("S2C")&&channel.equals("modularui2"))observeMuiOpen(payload);
            int bytes=payload instanceof byte[]?((byte[])payload).length:((Number)Mui2ClientQa.call(payload,"readableBytes")).intValue();
            add("phase"+owner.phase+":"+direction+":"+channel+":packets",1);
            add("phase"+owner.phase+":"+direction+":"+channel+":payloadBytes",bytes);
        }catch(Throwable failure){add("measurementErrors",1);}
    }
    void observeMuiOpen(Object payload)throws Exception {
        // MUI2 .92 registers the same OpenGuiPacket class at 2 then 3. Forge retains both
        // decoder entries, but its class-to-id map is overwritten to 3 for encoding on either side.
        // Parse a duplicate's header only: no reader-index changes or packet execution on the live payload.
        Object bytes=payload instanceof byte[]?io.netty.buffer.Unpooled.wrappedBuffer((byte[])payload)
            :Mui2ClientQa.call(payload,"duplicate");
        try{
        Object buffer=Mui2ClientQa.type("net.minecraft.network.PacketBuffer")
            .getConstructor(io.netty.buffer.ByteBuf.class).newInstance(bytes);
        if(((Number)Mui2ClientQa.call(buffer,"readableBytes")).intValue()<3)return;
        int discriminator=((Number)Mui2ClientQa.call(buffer,"readUnsignedByte")).intValue();
        if(discriminator!=2&&discriminator!=3)return;
        int window=((Number)Mui2ClientQa.call(buffer,"readVarIntFromBuffer|func_150792_a")).intValue();
        int network=((Number)Mui2ClientQa.call(buffer,"readVarIntFromBuffer|func_150792_a")).intValue();
        String factory=String.valueOf(Mui2ClientQa.call(Mui2ClientQa.type(
            "com.cleanroommc.modularui.network.NetworkUtils"),"readStringSafe",buffer));
        JsonObject proof=new JsonObject();proof.addProperty("factory",factory);proof.addProperty("windowId",window);
        proof.addProperty("networkId",network);proof.addProperty("observedDiscriminator",discriminator);
        proof.addProperty("scope","Read-only actual S2C OpenGuiPacket header; MUI2 2.3.92 valid decode ids 2/3, Forge final class-to-id encoding 3");
        lastMuiOpen=proof;
        }finally{if(payload instanceof byte[])((io.netty.buffer.ByteBuf)bytes).release();}
    }
    void add(String key,long n){AtomicLong value=values.get(key);if(value==null){AtomicLong fresh=new AtomicLong();AtomicLong old=values.putIfAbsent(key,fresh);value=old==null?fresh:old;}value.addAndGet(n);}
    long muiPackets(){long n=0;for(Map.Entry<String,AtomicLong> e:values.entrySet())if(e.getKey().contains(":modularui2:packets"))n+=e.getValue().get();return n;}
    JsonObject snapshot(){JsonObject out=new JsonObject();for(Map.Entry<String,AtomicLong> e:values.entrySet())out.addProperty(e.getKey(),e.getValue().get());return out;}
    @Override public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{count("S2C",msg);super.channelRead(ctx,msg);}
    @Override public void write(ChannelHandlerContext ctx,Object msg,ChannelPromise promise)throws Exception{count("C2S",msg);super.write(ctx,msg,promise);}
}
