package com.paximum.paxassist.reservation;

import com.paximum.paxassist.chat.dto.ErrorResponse;
import com.paximum.paxassist.common.exception.AuthenticationRequiredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Reservations", description = "Rezervasyon işlemleri")
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "Rezervasyon önizleme",
               description = "Seçilen ürün için fiyat ve detay özeti döner; rezervasyon oluşturmaz.")
    @ApiResponse(responseCode = "200", description = "Önizleme özeti",
            content = @Content(schema = @Schema(implementation = ReservationPreviewResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Kimlik doğrulaması gerekli",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/preview")
    public ReservationPreviewResponse preview(@RequestBody @Valid ReservationPreviewRequest request) {
        return reservationService.preview(request, requireUserId());
    }

    @Operation(summary = "Rezervasyon oluştur",
               description = "Yolcu bilgileri ile birlikte rezervasyon oluşturur.")
    @ApiResponse(responseCode = "201", description = "Rezervasyon oluşturuldu",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validasyon hatası",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Kimlik doğrulaması gerekli",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@RequestBody @Valid CreateReservationRequest request) {
        return reservationService.create(request, requireUserId());
    }

    @Operation(summary = "Rezervasyon listesi",
               description = "Oturum açmış kullanıcının tüm rezervasyonlarını döner.")
    @ApiResponse(responseCode = "200", description = "Rezervasyon listesi")
    @ApiResponse(responseCode = "401", description = "Kimlik doğrulaması gerekli",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping
    public List<ReservationResponse> list() {
        return reservationService.findAll(requireUserId());
    }

    @Operation(summary = "Rezervasyon detayı",
               description = "Belirtilen rezervasyonun detaylarını döner.")
    @ApiResponse(responseCode = "200", description = "Rezervasyon detayı",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "401", description = "Kimlik doğrulaması gerekli",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Rezervasyon bulunamadı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public ReservationResponse getById(@PathVariable Long id) {
        return reservationService.findById(id, requireUserId());
    }

    @Operation(summary = "Rezervasyon iptali",
               description = "Belirtilen rezervasyonu iptal eder.")
    @ApiResponse(responseCode = "200", description = "İptal edildi",
            content = @Content(schema = @Schema(implementation = ReservationResponse.class)))
    @ApiResponse(responseCode = "401", description = "Kimlik doğrulaması gerekli",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Rezervasyon bulunamadı",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservationService.cancel(id, requireUserId());
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new AuthenticationRequiredException();
    }
}
