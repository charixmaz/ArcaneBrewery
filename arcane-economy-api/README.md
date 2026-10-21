# ArcaneEconomy public API

Coordinates: `me.arcanebrewery:arcane-economy-api:0.1.0-SNAPSHOT` (Java 25).
This is a plain Java library with **no production dependencies**, Bukkit types,
database code or plugin descriptor. It is not a third server plugin.

Build independently with `mvn --batch-mode -f arcane-economy-api/pom.xml clean verify`.
Install locally with `clean install` before building either consuming project.
There is no public Maven release repository yet; integrations must install the
artifact from source or use their organization's artifact repository.

ArcaneEconomy packages the API once. Consumers use Maven `provided` scope and
declare `softdepend: [ArcaneEconomy]` in their Bukkit `plugin.yml`. Do not shade,
relocate, copy these interfaces or add another runtime copy through `libraries`.
This preserves the same `Class` identity for Bukkit ServicesManager.
See [Paper's plugin.yml documentation](https://docs.papermc.io/paper/dev/plugin-yml/).

An optional integration must isolate all references to these classes behind a
provider-presence check. ArcaneBrewery's `OptionalArcaneEconomy` demonstrates an
API-free bootstrap and a separately linked connection. Code in that connection
obtains `getRegistration(ArcaneEconomyService.class)` from ServicesManager on the
server thread, checks the owning plugin is enabled, and observes registration,
unregistration and plugin disable/enable events. Absence or incompatible API
versions must disable only economy operations.

`ArcaneEconomyService.API_VERSION` identifies the contract; `apiVersion()` lets
consumers reject unsupported providers. Interface package names are unchanged.
The four public types are `ArcaneEconomyService`, `EconomyResult`,
`EconomyTransactionRecord` and `TransactionLookup`.

## Calling contract

- All service methods are synchronous, potentially blocking SQLite calls. Invoke
  them off the Paper main thread. The API itself does not schedule work.
- Amounts and balances are `BigDecimal` end to end. Use decimal strings or exact
  integer minor units; do not pass through floating point. Currency precision and
  balance bounds remain provider configuration. Unsupported precision is rejected,
  never silently rounded. `canAfford` is advisory; the mutation is authoritative.
- Persist a globally unique transaction UUID and the exact request before calling
  a mutation. Replay the **same UUID, operation, account(s), amount** for retries.
  Never generate a replacement UUID inside an adapter.
- `APPLIED` means newly committed. `DUPLICATE` means this exact request was already
  committed, with no second mutation. `EconomyResult.applied()` deliberately only
  describes the former. A different request reusing the UUID is `INVALID`.
- `INVALID`, `OUT_OF_RANGE` and `INSUFFICIENT_FUNDS` are confirmed rejections.
  Storage exceptions are **not** proof that a write did not commit. Keep the
  reservation, query `transaction(id)` and reconcile with the original request.
  Do not pay again or restore a sold item on an ambiguous write response.
- Shutdown unregisters the provider; retained ledger references reject new calls.
  Already running operations may finish. A full plugin JAR/classloader replacement
  requires a server restart to guarantee a shared API class identity. Hot-reload
  tools and Folia are not supported runtime assumptions.

## Example provider lookup

Only load this code after ArcaneEconomy presence has been established:

```java
// Server thread: discover and retain a lifecycle-guarded service binding.
var registration = plugin.getServer().getServicesManager()
        .getRegistration(ArcaneEconomyService.class);
if (registration != null && registration.getPlugin().isEnabled()) {
    ArcaneEconomyService service = registration.getProvider();
    // On a worker, after validating that the binding remains active:
    // service.deposit(playerId, new BigDecimal("12.50"), persistedTransactionId);
}
```

Do not cache a raw provider forever; invalidate it on disable/unregistration and
refresh it on registration. Marshal any player inventory or message updates back
to the server thread. Do not join an async settlement future on that thread.
