package com.kpgfinance.accounts.service.impl;

import com.kpgfinance.accounts.dto.AccountsDto;
import com.kpgfinance.accounts.dto.CardsDto;
import com.kpgfinance.accounts.dto.CustomerDetailsDto;
import com.kpgfinance.accounts.dto.LoansDto;
import com.kpgfinance.accounts.entity.Accounts;
import com.kpgfinance.accounts.entity.Customer;
import com.kpgfinance.accounts.exception.ResourceNotFoundException;
import com.kpgfinance.accounts.mapper.AccountsMapper;
import com.kpgfinance.accounts.mapper.CustomerMapper;
import com.kpgfinance.accounts.repository.AccountsRepository;
import com.kpgfinance.accounts.repository.CustomerRepository;
import com.kpgfinance.accounts.service.ICustomersService;
import com.kpgfinance.accounts.service.client.CardsClient;
import com.kpgfinance.accounts.service.client.LoansClient;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CustomersServiceImpl implements ICustomersService {

    private static final Logger logger = LoggerFactory.getLogger(CustomersServiceImpl.class);
    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;
    private CardsClient cardsClient;
    private LoansClient loansClient;


    @Override
    public CustomerDetailsDto fetchCustomerDetails(String mobileNumber) {
        logger.debug("Looking up customer by mobileNumber: {}", mobileNumber);
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
        );

        CustomerDetailsDto customerDetailsDto = CustomerMapper.mapToCustomerDetailsDto(customer, new CustomerDetailsDto());
        customerDetailsDto.setAccountsDto(AccountsMapper.mapToAccountsDto(accounts, new AccountsDto()));

        logger.debug("Calling loans service for mobileNumber: {}", mobileNumber);
        ResponseEntity<LoansDto> loansDtoResponseEntity = loansClient.fetchLoanDetails(mobileNumber);
        if(null != loansDtoResponseEntity) {
            customerDetailsDto.setLoansDto(loansDtoResponseEntity.getBody());
        } else {
            logger.warn("Loans service call failed or fell back for mobileNumber: {}", mobileNumber);
        }

        logger.debug("Calling cards service for mobileNumber: {}", mobileNumber);
        ResponseEntity<CardsDto> cardsDtoResponseEntity = cardsClient.fetchCardDetails(mobileNumber);
        if(null != cardsDtoResponseEntity) {
            customerDetailsDto.setCardsDto(cardsDtoResponseEntity.getBody());
        } else {
            logger.warn("Cards service call failed or fell back for mobileNumber: {}", mobileNumber);
        }
        return customerDetailsDto;

    }
}
