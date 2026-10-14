package com.dfs.corporate.web;

import com.dfs.corporate.domain.PartnerAppUser;
import com.dfs.corporate.domain.Party;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.util.IdentityFormats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent API looks up owner CNIC by wallet mobile when DFS accountDetails returns nidNo=null.
 * Auth: same X-Portal-Key as DFS portal calls (CORPORATE_PORTAL_API_KEY).
 */
@RestController
@RequestMapping("/api/public/agent-identity")
public class AgentIdentityController {

    private final PartyRepository partyRepository;
    private final PartnerAppUserRepository partnerAppUserRepository;
    private final String portalKey;

    public AgentIdentityController(
            PartyRepository partyRepository,
            PartnerAppUserRepository partnerAppUserRepository,
            @Value("${dfs.portal-api.portal-key:}") String portalKey) {
        this.partyRepository = partyRepository;
        this.partnerAppUserRepository = partnerAppUserRepository;
        this.portalKey = portalKey != null ? portalKey.trim() : "";
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> byMobile(
            @RequestParam String mobile,
            @RequestHeader(value = "X-Portal-Key", required = false) String key) {
        if (portalKey.isBlank() || key == null || !portalKey.equals(key.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "responsecode", "401",
                    "messages", "Invalid or missing X-Portal-Key"
            ));
        }
        String digits = IdentityFormats.phoneDigits(mobile);
        if (digits == null || digits.length() < 10) {
            return ResponseEntity.badRequest().body(Map.of(
                    "responsecode", "400",
                    "messages", "mobile required"
            ));
        }
        String last10 = digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;

        String cnic = null;
        String title = null;

        // Partner app user first (KYC CNIC)
        List<PartnerAppUser> users = partnerAppUserRepository.findAll();
        for (PartnerAppUser u : users) {
            String ph = IdentityFormats.phoneDigits(u.getPhone());
            if (ph != null && (ph.equals(digits) || ph.endsWith(last10))) {
                cnic = IdentityFormats.cnicDigits(u.getCnicNumber());
                if (cnic != null && cnic.length() >= 13) {
                    title = u.getFullName();
                    break;
                }
            }
        }

        if (cnic == null || cnic.length() < 13) {
            for (Party p : partyRepository.findAll()) {
                String ph = IdentityFormats.phoneDigits(p.getPhone());
                if (ph != null && (ph.equals(digits) || ph.endsWith(last10))) {
                    cnic = IdentityFormats.cnicDigits(p.getCnicNumber());
                    if (cnic != null && cnic.length() >= 13) {
                        title = p.getFullName() != null ? p.getFullName() : p.getBusinessName();
                        break;
                    }
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mobileNumber", digits);
        if (cnic != null && cnic.length() >= 13) {
            data.put("nidNo", cnic.length() > 13 ? cnic.substring(0, 13) : cnic);
            data.put("cnicNumber", data.get("nidNo"));
        } else {
            data.put("nidNo", null);
            data.put("cnicNumber", null);
        }
        if (title != null) data.put("accountTitle", title);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("responsecode", "000");
        body.put("messages", "SUCCESS");
        body.put("data", data);
        return ResponseEntity.ok(body);
    }
}
