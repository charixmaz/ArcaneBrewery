package me.arcanebrewery.arcaneBrewery.application.economy;

/** A write may have committed. Retain the reservation and reconcile the SAME ID;
 * do not automatically restore the asset or retry with a fresh transaction ID. */
public final class EconomyOperationUncertainException extends IllegalStateException {
    public EconomyOperationUncertainException(Throwable cause) {
        super("Economy outcome requires reconciliation using the original transaction ID", cause);
    }
}
