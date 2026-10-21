# Foundation V1.12: hangover, withdrawal and recovery lifecycle

V1.12 extends canonical consumption into a persistent player recovery lifecycle.

## Persistent condition

Player state now includes:
- intoxication;
- tolerance;
- poisoning;
- dependence;
- hangover load;
- withdrawal load;
- wall-clock update timestamp.

These values remain gameplay simulation values, not medical measurements.

## Hangovers

Heavy intoxication and poisoning can generate hangover load. Hangovers decay using elapsed wall-clock time, so disconnecting does not pause recovery.

## Withdrawal

Players with sufficiently high dependence and low intoxication can accumulate withdrawal load while sober.

Drinking can temporarily reduce withdrawal load, but it does not erase dependence. Dependence itself continues to decay slowly over time.

## Status

The lifecycle exposes:
- CLEAR
- BUZZED
- DRUNK
- HANGOVER
- SEVERE_HANGOVER
- WITHDRAWAL
- SEVERE_WITHDRAWAL

The Bukkit layer turns these states into temporary presentation effects. The pure lifecycle remains authoritative.

## Runtime recovery

The Bukkit listener:
- recovers state when a player joins;
- refreshes online players periodically;
- persists recovered values back to player PDC;
- applies short effects that are refreshed rather than long effects that outlive the model.

This keeps offline and online recovery behavior consistent.

## Next phase

The next major gameplay phase should be the alchemy system: catalysts, magical affinities, instability, beneficial effects, curses, hallucinations and dangerous reactions. It should build on canonical process identity and consumption metadata rather than hardcoded potion names.
