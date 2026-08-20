package com.heihun.easytiermd3.domain.usecase

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.domain.repository.ConnectionRepository
import javax.inject.Inject

class ConnectNetworkUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    suspend operator fun invoke(networkId: String, config: EasyTierConfig): Result<Unit> =
        connectionRepository.connect(networkId, config)
}

class DisconnectNetworkUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    suspend operator fun invoke(): Result<Unit> = connectionRepository.disconnect()
}

class RestartNetworkUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) {
    suspend operator fun invoke(config: EasyTierConfig): Result<Unit> =
        connectionRepository.restart(config)
}
