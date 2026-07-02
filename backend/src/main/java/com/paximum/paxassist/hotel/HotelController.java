package com.paximum.paxassist.hotel;

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

@Tag(name = "Hotels", description = "Otel arama")
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {

    private final HotelSearchService hotelSearchService;

    public HotelController(HotelSearchService hotelSearchService) {
        this.hotelSearchService = hotelSearchService;
    }

    @Operation(summary = "Otel ara",
               description = "Verilen kriterlere göre TourVisio üzerinden otel arama yapar.")
    @ApiResponse(responseCode = "200", description = "Otel listesi")
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/search")
    public List<HotelProduct> search(@RequestBody @Valid HotelSearchRequest request) {
        return hotelSearchService.search(request);
    }
}
