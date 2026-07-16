package com.paximum.paxassist.orchestrator.mapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class GeoCountryResolverTest {

    private final GeoCountryResolver resolver = new GeoCountryResolver();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequestWithCountry(String country) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (country != null) {
            request.addHeader(GeoCountryResolver.COUNTRY_HEADER, country);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void readsTheCountryFromTheCloudflareHeader() {
        bindRequestWithCountry("DE");
        assertThat(resolver.currentCountry()).contains("DE");
    }

    @Test
    void normalizesToUppercase() {
        bindRequestWithCountry(" de ");
        assertThat(resolver.currentCountry()).contains("DE");
    }

    /** No Cloudflare in front today, so the header is simply absent — callers must get the fallback. */
    @Test
    void isEmptyWhenTheHeaderIsAbsent() {
        bindRequestWithCountry(null);
        assertThat(resolver.currentCountry()).isEmpty();
    }

    /** Cloudflare sends XX when it cannot geolocate; that is "unknown", not a country. */
    @Test
    void isEmptyForCloudflaresUnknownPlaceholder() {
        bindRequestWithCountry("XX");
        assertThat(resolver.currentCountry()).isEmpty();
    }

    /** Background jobs and unit tests run with no request bound — must fall back, never throw. */
    @Test
    void isEmptyOutsideAnHttpRequest() {
        assertThat(resolver.currentCountry()).isEmpty();
    }

    @Test
    void anAbsentHeaderYieldsTheTryDefault() {
        bindRequestWithCountry(null);
        assertThat(CurrencyByCountry.resolve(null, resolver.currentCountry().orElse(null)))
                .isEqualTo("TRY");
    }
}
