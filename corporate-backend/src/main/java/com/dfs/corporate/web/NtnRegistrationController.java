package com.dfs.corporate.web;

import com.dfs.corporate.domain.NtnDirectoryAccount;
import com.dfs.corporate.domain.NtnDirectoryEntry;
import com.dfs.corporate.repository.NtnDirectoryRepository;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/registration")
public class NtnRegistrationController {

    private final NtnDirectoryRepository ntnDirectoryRepository;

    public NtnRegistrationController(NtnDirectoryRepository ntnDirectoryRepository) {
        this.ntnDirectoryRepository = ntnDirectoryRepository;
    }

    @PostMapping("/ntn/lookup")
    public Map<String, Object> lookup(@RequestBody Map<String, String> body) {
        String ntn = body.get("ntnNumber");
        if (ntn == null || ntn.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ntnNumber is required");
        }
        NtnDirectoryEntry entry = ntnDirectoryRepository.findByNtnNumberIgnoreCase(ntn.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "No mock corporate record found for NTN: " + ntn.trim()));
        return toMap(entry);
    }

    private Map<String, Object> toMap(NtnDirectoryEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ntnNumber", e.getNtnNumber());
        m.put("legalName", e.getLegalName());
        m.put("entityType", e.getEntityType());
        m.put("taxCountry", e.getTaxCountry());
        m.put("registeredAddress", e.getRegisteredAddress());
        m.put("natureOfBusiness", e.getNatureOfBusiness());
        m.put("contactPhone", e.getContactPhone());
        m.put("contactEmail", e.getContactEmail());
        List<Map<String, Object>> accounts = e.getAccounts().stream().map(this::accountMap).toList();
        m.put("accounts", accounts);
        return m;
    }

    private Map<String, Object> accountMap(NtnDirectoryAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountNo", a.getAccountNo());
        m.put("accountTitle", a.getAccountTitle());
        m.put("iban", a.getIban());
        m.put("accountType", a.getAccountType());
        m.put("currency", a.getCurrency());
        m.put("status", a.getStatus());
        return m;
    }
}
