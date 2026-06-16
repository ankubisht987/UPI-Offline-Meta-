package com.ankush.upi_offline_meta.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ankush.upi_offline_meta.model.AccountRepository;

@Service
public class  DemoService  {
    
    @Autowired private AccountRepository account;
    @Autowired private AccountRepository accountRepository;
    
}
