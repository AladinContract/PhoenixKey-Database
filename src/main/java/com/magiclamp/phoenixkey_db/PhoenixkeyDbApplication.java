package com.magiclamp.phoenixkey_db;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PhoenixkeyDbApplication {

    public static void main(String[] args) {
        // dotenv-java reads .env from the working directory (project root)
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        for (DotenvEntry entry : dotenv.entries()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }

        SpringApplication.run(PhoenixkeyDbApplication.class, args);
    }

}
