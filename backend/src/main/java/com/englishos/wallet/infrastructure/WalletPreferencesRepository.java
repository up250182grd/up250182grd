package com.englishos.wallet.infrastructure;

import com.englishos.wallet.domain.WalletPreferences;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletPreferencesRepository extends JpaRepository<WalletPreferences, Long> {
}
