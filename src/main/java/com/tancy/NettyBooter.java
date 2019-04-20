package com.tancy;

import com.tancy.netty.WCServer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

//监听spring-boot，当整个容器加载完后，可以开始启动netty
@Component
public class NettyBooter implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        if (contextRefreshedEvent.getApplicationContext().getParent() == null) {
            try {
                WCServer.getInstance().start();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
