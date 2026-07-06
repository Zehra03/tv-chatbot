/**
 * Bölge kimliği — "AI bölgesi koyu, kontrollü bölge açık"
 * (docs/frontend-design-plan.md). Rotalar `handle: { zone }` ile işaretlenir;
 * Layout `useMatches()` üzerinden okur ve yüzeyi ona göre boyar.
 *
 * 'ai'         → gece uçuşu yüzeyi (brand-navy + cam). Chat ve arama sonuçları.
 * 'controlled' → açık, sade "resmî" yüzey. Rezervasyon akışı (AI devre dışı).
 *
 * İşaretsiz rotalar 'controlled' varsayılır — bir sayfa ancak içeriği koyu
 * yüzeye taşındığında 'ai' işareti alır (her PR yayınlanabilir kalır).
 */
export type Zone = 'ai' | 'controlled'

export interface ZoneHandle {
  zone: Zone
}

/** useMatches() sonucundan en içteki bölge işaretini çıkarır. */
export function zoneFromMatches(matches: Array<{ handle?: unknown }>): Zone {
  for (let i = matches.length - 1; i >= 0; i--) {
    const handle = matches[i].handle as Partial<ZoneHandle> | undefined
    if (handle?.zone) return handle.zone
  }
  return 'controlled'
}
