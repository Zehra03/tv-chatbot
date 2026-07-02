# Chat

DELETE /api/v1/chat/{sessionId}
POST /api/v1/chat
GET /api/v1/chat/{sessionId}

# Hotel

POST /api/v1/hotels/search

# Flight

POST /api/v1/flights/search

# Reservation

POST /api/v1/reservations/preview
POST /api/v1/reservations
GET /api/v1/reservations
GET /api/v1/reservations/{id}
PATCH /api/v1/reservations/{id}/cancel

# Auth

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/logout
GET /api/v1/auth/me
