package com.kpgfinance.accounts;

import com.kpgfinance.accounts.dto.AccountsContactInfoDto;
import com.kpgfinance.accounts.service.client.CardsClient;
import com.kpgfinance.accounts.service.client.LoansClient;
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
import org.springframework.web.service.registry.ImportHttpServices;


@SpringBootApplication
@EnableDiscoveryClient
@ImportHttpServices(group = "loans", types= LoansClient.class)
@ImportHttpServices(group = "cards", types= CardsClient.class)
@EnableConfigurationProperties(value = {AccountsContactInfoDto.class})
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
@OpenAPIDefinition(
		info = @Info(
				title = "Accounts microservice REST API Documentation",
				description = "KPGFinance Accounts microservice REST API Documentation",
				version = "v1",
				contact = @Contact(
						name = "Ansel Fernandes",
						email = "anselfernandes77@gmail.com",
						url = "https://www.kpgfinance.com"
				),
				license = @License(
						name = "Apache 2.0",
						url = "https://www.kpgfinance.com"
				)
		),
		externalDocs = @ExternalDocumentation(
				description =  "KPGFinance Accounts microservice REST API Documentation",
				url = "https://www.kpgfinance.com/swagger-ui.html"
		)
)
public class AccountsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountsApplication.class, args);
	}

}
