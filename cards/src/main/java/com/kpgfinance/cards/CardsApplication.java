package com.kpgfinance.cards;

import com.kpgfinance.cards.dto.CardsContactInfoDto;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableConfigurationProperties(value = {CardsContactInfoDto.class})
@SpringBootApplication
/*@ComponentScans({ @ComponentScan("com.kpgfinance.cards.controller") })
@EnableJpaRepositories("com.kpgfinance.cards.repository")
@EntityScan("com.kpgfinance.cards.model")*/
@EnableDiscoveryClient
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
@OpenAPIDefinition(
		info = @Info(
				title = "Cards microservice REST API Documentation",
				description = "KPGFinance Cards microservice REST API Documentation",
				version = "v1",
				contact = @Contact(
						name = "Madan Reddy",
						email = "tutor@kpgfinance.com",
						url = "https://www.kpgfinance.com"
				),
				license = @License(
						name = "Apache 2.0",
						url = "https://www.kpgfinance.com"
				)
		),
		externalDocs = @ExternalDocumentation(
				description = "KPGFinance Cards microservice REST API Documentation",
				url = "https://www.kpgfinance.com/swagger-ui.html"
		)
)
public class CardsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardsApplication.class, args);
	}
}
