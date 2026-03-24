package com.ninabit.bono.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI bonoOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("API Backend - Skyworth Bono Vendedores")
                                                .description("API REST para el sistema de gestión de bonos, ventas y vendedores de Skyworth.")
                                                .version("v1.0.0")
                                                .contact(new Contact()
                                                                .name("Soporte Técnico Total IT Solutions")
                                                                .email("info@totalit.com.bo")
                                                                .url("https://totalit.com.bo"))
                                                .license(new License().name("Propiedad Privada")
                                                                .url("https://skyworth.bo")));
        }
}
