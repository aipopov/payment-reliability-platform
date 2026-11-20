package tech.inita.ledgerservice.account;

import lombok.Getter;

/**
 * Назначение счёта
 * <p>Отвечает на вопрос: для чего используется этот счёт?</p>
 */

@Getter
public enum AccountType {
    /**
     * Доступный
     * <p>Средства, доступные для использования</p>
     */
    AVAILABLE(AccountClass.LIABILITY),

    /**
     * Зарезервированный
     * <p>Средства, заблокированные под операцию</p>
     */
    RESERVED(AccountClass.LIABILITY),

    /**
     * Расчётный
     * <p>Средства, учитываемые в расчётах с провайдером</p>
     */
    CLEARING(AccountClass.LIABILITY),

    /**
     * Денежные средства
     * <p>Денежные активы платформы</p>
     */
    CASH(AccountClass.ASSET);

    private final AccountClass accountClass;

    AccountType(AccountClass accountClass) {
        this.accountClass = accountClass;
    }

}
