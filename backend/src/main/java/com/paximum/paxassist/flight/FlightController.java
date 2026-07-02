package com.paximum.paxassist.flight;

import com.paximum.paxassist.chat.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Flights", description = "Uçuş arama")
@RestController
@RequestMapping("/api/v1/flights")
public class FlightController {

    private final FlightSearchService flightSearchService;

    public FlightController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @Operation(summary = "Uçuş ara",
               description = "Verilen kriterlere göre TourVisio üzerinden uçuş arama yapar.")
    @ApiResponse(responseCode = "200", description = "Uçuş listesi")
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/search")
    public List<FlightProduct> search(@RequestBody @Valid FlightSearchRequest request) {
        return flightSearchService.search(request);
    }
}
