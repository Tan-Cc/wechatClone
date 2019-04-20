package com.tancy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import tk.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
//扫描mybatis mapper包路径
@MapperScan(basePackages = "com.tancy.mapper")
//扫描所有需要的包
@ComponentScan(basePackages = {"com.tancy", "org.n3r.idworker"})
public class WechatCloneApplication {

	@Bean
	public SpringUtil getSpringUtil() {
		return new SpringUtil();
	}

	public static void main(String[] args) {

		SpringApplication.run(WechatCloneApplication.class, args);
	}

}
