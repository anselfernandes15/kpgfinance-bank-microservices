package com.kpgfinance.accounts.service.client;

import com.kpgfinance.accounts.dto.LoansDto;
import org.springframework.cloud.client.circuitbreaker.httpservice.HttpServiceFallback;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@HttpServiceFallback(value= LoansFallback.class, group = "loans")
public class LoansFallback {

    public ResponseEntity<LoansDto> fetchLoanDetails(String correlationId, String mobileNumber){
        return null;
    }
}
