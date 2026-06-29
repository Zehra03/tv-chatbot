.PHONY: test-docker test-backend-docker test-frontend-docker

test-docker:
	docker compose -f docker-compose.test.yml run --rm backend-test
	docker compose -f docker-compose.test.yml run --rm frontend-test

test-backend-docker:
	docker compose -f docker-compose.test.yml run --rm backend-test

test-frontend-docker:
	docker compose -f docker-compose.test.yml run --rm frontend-test
