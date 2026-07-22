package br.com.mkfinance;

import org.springframework.boot.SpringApplication;

public class TestMkFinanceApplication {

    public static void main(String[] args) {
        SpringApplication.from(MkFinanceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
