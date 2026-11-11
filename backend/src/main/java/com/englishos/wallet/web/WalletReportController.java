package com.englishos.wallet.web;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.shared.web.CsvResponses;
import com.englishos.wallet.application.LedgerReportService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WalletReportController {

    private final LedgerReportService reports;
    private final LearnerProfiles profiles;

    WalletReportController(LedgerReportService reports, LearnerProfiles profiles) {
        this.reports = reports;
        this.profiles = profiles;
    }

    @GetMapping(path = "/api/v1/admin/reports/ledger.csv", produces = "text/csv")
    ResponseEntity<byte[]> ledger(@AuthenticationPrincipal CurrentUser actor,
            @RequestParam(required = false) String month) {
        var p = CsvResponses.Period.of(month, profiles.zoneOf(actor.id()));
        return CsvResponses.csv("libro-mayor-" + p.month() + ".csv", reports.ledgerCsv(p.from(), p.to()));
    }
}
