package com.paximum.paxassist.orchestrator.mapper;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the country a request came from, used only to pick a DEFAULT search currency
 * ({@link CurrencyByCountry}).
 *
 * <p>Reads Cloudflare's {@code CF-IPCountry} header. Nothing sets that header today — the backend
 * is not behind Cloudflare yet (see {@code docker-compose.yml}) — so this currently returns empty
 * and callers fall back to TRY. That is deliberate: the seam exists now, and the guess starts
 * working the day the deployment moves behind Cloudflare, with no code change here.
 *
 * <p>On trusting the header: a client can forge it, and that is acceptable ONLY because the value
 * merely preselects a currency the user is free to change anyway — forging it buys nothing the UI
 * does not already offer. This resolver must never be extended to fill nationality, which decides
 * the provider's pricing contract; that would hand a client control over the price it is quoted.
 *
 * <p>Empty outside an HTTP request (background jobs, unit tests) rather than throwing, so callers
 * get the TRY fallback instead of an error.
 */
@Component
public class GeoCountryResolver {

    static final String COUNTRY_HEADER = "CF-IPCountry";

    /** Cloudflare's placeholder when it cannot geolocate the address (also sent for Tor exits). */
    private static final String UNKNOWN_COUNTRY = "XX";

    /** @return the ISO-3166 alpha-2 country of the current request, or empty when unavailable */
    public Optional<String> currentCountry() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty(); // no HTTP request bound to this thread
        }
        String country = servletAttributes.getRequest().getHeader(COUNTRY_HEADER);
        if (country == null || country.isBlank() || UNKNOWN_COUNTRY.equalsIgnoreCase(country.strip())) {
            return Optional.empty();
        }
        return Optional.of(country.strip().toUpperCase(Locale.ROOT));
    }
}
