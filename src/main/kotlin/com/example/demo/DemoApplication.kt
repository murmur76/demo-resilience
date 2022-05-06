package com.example.demo

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

private val log = KotlinLogging.logger {}

@SpringBootApplication
class DemoApplication

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)

	val circuit = CircuitBreaker.of("A", getDefaultConfig())
	circuit.getEventPublisher()
		.onStateTransition {
			log.info("CIRCUIT BREAKER " + it.getStateTransition().toString())
		}

	for (i in 1..50) {
		Mono.zip(
			request().transformDeferred(CircuitBreakerOperator.of(circuit)),
			request().transformDeferred(CircuitBreakerOperator.of(circuit)),
			request().transformDeferred(CircuitBreakerOperator.of(circuit))
		).onErrorResume { e ->
			log.error("error: $i", e)
			Mono.empty()
		}.block()
		Thread.sleep(1000)
	}
}

private fun request(): Mono<String> =
	WebClient.create("http://localhost:8080").get().retrieve().bodyToMono(String::class.java)

private fun getDefaultConfig(): CircuitBreakerConfig = CircuitBreakerConfig.custom()
	.automaticTransitionFromOpenToHalfOpenEnabled(true)
	.slidingWindow(10, 10, CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
	.failureRateThreshold(50F)
	.slowCallRateThreshold(100f)
	.slowCallDurationThreshold(Duration.ofSeconds(10))
	.waitDurationInOpenState(Duration.ofSeconds(5))
	.build()

