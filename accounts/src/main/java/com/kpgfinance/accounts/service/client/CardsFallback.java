package com.kpgfinance.accounts.service.client;

import com.kpgfinance.accounts.dto.CardsDto;
import com.kpgfinance.accounts.dto.LoansDto;
import org.springframework.cloud.client.circuitbreaker.httpservice.HttpServiceFallback;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@HttpServiceFallback(value= CardsFallback.class, group = "cards")
public class CardsFallback {

    public ResponseEntity<CardsDto> fetchCardDetails(String correlationId, String mobileNumber){
        return null;
    }
}
