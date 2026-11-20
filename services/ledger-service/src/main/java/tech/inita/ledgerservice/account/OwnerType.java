package tech.inita.ledgerservice.account;

/**
 * Тип владельца счёта.
 * <p>Отвечает на вопрос: кому принадлежит счёт?</p>
 */

public enum OwnerType {
    /// Клиент платформы
    CUSTOMER,

    /// Сама платформа
    PLATFORM,

    /// Внешний платёжный провайдер
    PROVIDER
}
