package com.dfs.corporate.config;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.BrandRepository;
import com.dfs.corporate.repository.PartyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedAdmin(PartyRepository partyRepository,
                                AccountRepository accountRepository,
                                BrandRepository brandRepository,
                                PasswordEncoder passwordEncoder) {
        return args -> {
            brandRepository.findByCodeIgnoreCase("DFS").ifPresent(dfs ->
                    partyRepository.findAll().stream()
                            .filter(p -> p.getBrandId() == null)
                            .forEach(p -> {
                                p.setBrandId(dfs.getId());
                                partyRepository.save(p);
                            }));

            String adminEmail = "admin@dfscorporate.local";
            if (!accountRepository.existsByEmailIgnoreCase(adminEmail)) {
                Party party = new Party();
                party.setPublicId(UUID.randomUUID().toString());
                party.setPartyType(PartyType.MERCHANT);
                party.setStatus(PartyStatus.ACTIVE);
                party.setFullName("Platform Admin");
                party.setBusinessName("Bank Islami Corporate");
                party.setEmail(adminEmail);
                party.setPhone("0000000000");
                party.setCountry("Pakistan");
                brandRepository.findByCodeIgnoreCase("DFS")
                        .map(Brand::getId)
                        .ifPresent(party::setBrandId);
                Party saved = partyRepository.save(party);

                Account account = new Account();
                account.setPublicId(UUID.randomUUID().toString());
                account.setPartyId(saved.getId());
                account.setEmail(adminEmail);
                account.setPasswordHash(passwordEncoder.encode("Admin@123"));
                account.setRole(Role.PLATFORM_ADMIN);
                account.setStatus(AccountStatus.ACTIVE);
                account.setFirstLogin(false);
                accountRepository.save(account);

                log.info("Seeded platform admin: {} / Admin@123", adminEmail);
            }

            seedBranchUser(partyRepository, accountRepository, brandRepository, passwordEncoder);
        };
    }

    private void seedBranchUser(PartyRepository partyRepository,
                                AccountRepository accountRepository,
                                BrandRepository brandRepository,
                                PasswordEncoder passwordEncoder) {
        String branchEmail = "branch@bankislami.local";
        if (accountRepository.existsByEmailIgnoreCase(branchEmail)) {
            return;
        }
        Party party = new Party();
        party.setPublicId(UUID.randomUUID().toString());
        party.setPartyType(PartyType.MERCHANT);
        party.setStatus(PartyStatus.ACTIVE);
        party.setFullName("Branch Operations");
        party.setBusinessName("Bank Islami Branch Desk");
        party.setEmail(branchEmail);
        party.setPhone("02100000000");
        party.setCountry("Pakistan");
        brandRepository.findByCodeIgnoreCase("DFS")
                .map(Brand::getId)
                .ifPresent(party::setBrandId);
        Party saved = partyRepository.save(party);

        Account account = new Account();
        account.setPublicId(UUID.randomUUID().toString());
        account.setPartyId(saved.getId());
        account.setEmail(branchEmail);
        account.setPasswordHash(passwordEncoder.encode("Branch@123"));
        account.setRole(Role.BRANCH_USER);
        account.setStatus(AccountStatus.ACTIVE);
        account.setFirstLogin(false);
        accountRepository.save(account);

        log.info("Seeded branch user: {} / Branch@123", branchEmail);
    }
}
