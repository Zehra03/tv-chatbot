package com.paximum.paxassist.hotel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paximum.paxassist.hotel.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@Profile("!mock & !demo")
public class TourVisioHotelApiClientImpl implements TourVisioHotelApiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tourvisio.url}")
    private String baseUrl;

    @Value("${tourvisio.agency}")
    private String agency;

    @Value("${tourvisio.user}")
    private String username;

    @Value("${tourvisio.password}")
    private String password;

    private String cachedToken;
    private Instant tokenExpiry;

    public TourVisioHotelApiClientImpl(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    private synchronized String getOrFetchToken() {
        if (cachedToken == null || tokenExpiry == null || Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            cachedToken = authenticate();
        }
        return cachedToken;
    }

    @Override
    public String authenticate() {
        String url = baseUrl + "/api/authenticationservice/login";
        LoginRequest request = new LoginRequest(agency, username, password);

        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url, request, LoginResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            LoginResponse loginRes = response.getBody();
            if (loginRes.header().success() && loginRes.body() != null) {
                String token = loginRes.body().token();
                String expiresOnStr = loginRes.body().expiresOn();
                try {
                    this.tokenExpiry = Instant.parse(expiresOnStr);
                } catch (Exception e) {
                    this.tokenExpiry = Instant.now().plusSeconds(3600);
                }
                return token;
            }
        }
        throw new RuntimeException("TourVisio authentication failed!");
    }

    @Override
    public AutocompleteResponse getArrivalAutocomplete(String query) {
        String url = baseUrl + "/api/productservice/getarrivalautocomplete";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getOrFetchToken());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("ProductType", 2);
        requestBody.put("Query", query);
        requestBody.put("Culture", "tr-TR");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<AutocompleteResponse> response = restTemplate.postForEntity(url, entity, AutocompleteResponse.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        }

        throw new RuntimeException("TourVisio Autocomplete failed!");
    }

    @Override
    public Object priceSearch(HotelSearchRequest criteria, String locationId) {
        String url = baseUrl + "/api/productservice/pricesearch";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getOrFetchToken());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("checkAllotment", true);
        requestBody.put("checkStopSale", true);
        requestBody.put("getOnlyDiscountedPrice", false);
        requestBody.put("getOnlyBestOffers", true);
        requestBody.put("productType", 2);

        Map<String, Object> location = new HashMap<>();
        location.put("id", locationId);
        location.put("type", 2);
        requestBody.put("arrivalLocations", List.of(location));

        Map<String, Object> room = new HashMap<>();
        room.put("adult", criteria.adult());
        if (criteria.childAges() != null && !criteria.childAges().isEmpty()) {
            room.put("childAges", criteria.childAges());
        }
        requestBody.put("roomCriteria", List.of(room));

        requestBody.put("nationality", criteria.nationality());
        requestBody.put("checkIn", criteria.checkIn());
        requestBody.put("night", criteria.night());
        requestBody.put("currency", criteria.currency());
        requestBody.put("culture", criteria.culture());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Object> response = restTemplate.postForEntity(url, entity, Object.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            // Map the raw TourVisio response (body.hotels[]) into typed HotelProduct cards, matching
            // what MockTourVisioHotelApiClient returns. Returning the raw map made
            // HotelController.toProducts() (and the chat/MCP pipeline) silently drop every result to [].
            return mapToHotelProducts(response.getBody());
        }

        throw new RuntimeException("TourVisio PriceSearch failed!");
    }

    @Override
    public List<HotelProduct> searchHotels(HotelSearchCriteria criteria) {
        try {
            AutocompleteResponse autocompleteRes = getArrivalAutocomplete(criteria.destination());
            if (autocompleteRes == null || autocompleteRes.body() == null || autocompleteRes.body().items().isEmpty()) {
                return List.of();
            }

            String locationId = null;
            for (AutocompleteResponse.Item item : autocompleteRes.body().items()) {
                if (item.city() != null && item.city().id() != null) {
                    locationId = item.city().id();
                    break;
                }
            }
            if (locationId == null) {
                return List.of();
            }

            String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
            HotelSearchRequest request = new HotelSearchRequest(
                criteria.destination(),
                tomorrow,
                1,
                2,
                List.of(),
                "TR",
                "TRY",
                "tr-TR"
            );

            // priceSearch already maps to HotelProduct cards; just unwrap defensively.
            Object result = priceSearch(request, locationId);
            return (result instanceof List<?> list)
                    ? list.stream()
                            .filter(HotelProduct.class::isInstance)
                            .map(HotelProduct.class::cast)
                            .toList()
                    : List.of();
        } catch (Exception e) {
            System.err.println("Legacy searchHotels failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Maps a raw TourVisio PriceSearch response ({@code body.hotels[]}) into the typed
     * {@link HotelProduct} cards the {@code /api/v1/hotels/search} endpoint, chat handler and
     * MCP tool consume. Takes the first offer's price / board / availability per hotel.
     *
     * <p>Package-private so {@code TourVisioHotelApiClientImplTest} can exercise the mapping
     * without a live TourVisio call (the RestTemplate calls are not unit-testable here).
     */
    List<HotelProduct> mapToHotelProducts(Object rawResult) {
        JsonNode root = objectMapper.valueToTree(rawResult);
        JsonNode hotelsNode = root.path("body").path("hotels");
        List<HotelProduct> products = new ArrayList<>();
        if (hotelsNode.isArray()) {
            for (JsonNode hotelNode : hotelsNode) {
                String id = hotelNode.path("id").asText();
                String name = hotelNode.path("name").asText();
                String city = hotelNode.path("city").path("name").asText();
                int stars = hotelNode.path("stars").asInt();

                JsonNode firstOffer = hotelNode.path("offers").path(0);
                BigDecimal price = BigDecimal.ZERO;
                String currency = "TRY";
                String board = "Unknown";
                boolean available = false;

                if (!firstOffer.isMissingNode()) {
                    price = new BigDecimal(firstOffer.path("price").path("amount").asText("0"));
                    currency = firstOffer.path("price").path("currency").asText("TRY");
                    board = firstOffer.path("rooms").path(0).path("boardName").asText("Unknown");
                    available = firstOffer.path("isAvailable").asBoolean(true);
                }

                // Absolute image URL; TourVisio omits it for many hotels → null (frontend placeholder).
                JsonNode thumbNode = hotelNode.path("thumbnailFull");
                String image = thumbNode.isTextual() && !thumbNode.asText().isBlank() ? thumbNode.asText() : null;

                products.add(new HotelProduct(id, name, city, stars, price, currency, board, available, image));
            }
        }
        return products;
    }
}
