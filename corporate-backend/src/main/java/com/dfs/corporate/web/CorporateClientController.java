package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.CorporateBillService;
import com.dfs.corporate.service.CorporateXpinService;
import com.dfs.corporate.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/corporate")
public class CorporateClientController {

    private final CorporateXpinService xpinService;
    private final CorporateBillService billService;

    public CorporateClientController(CorporateXpinService xpinService, CorporateBillService billService) {
        this.xpinService = xpinService;
        this.billService = billService;
    }

    @PostMapping("/xpins")
    @ResponseStatus(HttpStatus.CREATED)
    public XpinResponse generateXpin(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody GenerateXpinRequest req
    ) {
        return xpinService.generate(principal, req);
    }

    @GetMapping("/xpins")
    public List<XpinResponse> listXpins(@AuthenticationPrincipal AccountPrincipal principal) {
        return xpinService.list(principal);
    }

    @GetMapping("/bills")
    public List<CorporateBillResponse> listBills(@AuthenticationPrincipal AccountPrincipal principal) {
        return billService.list(principal);
    }

    @PostMapping(value = "/bills/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BillUploadResult uploadBills(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        return billService.uploadCsv(principal, file);
    }
}
