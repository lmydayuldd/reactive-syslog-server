package am.ik.syslog;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.ipc.netty.NettyInbound;
import reactor.ipc.netty.NettyOutbound;

public class SyslogHandler
		implements BiFunction<NettyInbound, NettyOutbound, Publisher<Void>> {
	private static final Logger log = LoggerFactory.getLogger("LOG");
	private static final Logger err = LoggerFactory.getLogger(SyslogHandler.class);

	@Override
	public Publisher<Void> apply(NettyInbound in, NettyOutbound out) {
		in.receive() //
				.asString() //
				.flatMapIterable(s -> Arrays.asList(s.split("(?<=\n)"))) //
				.windowUntil(s -> s.endsWith("\n")) //
				.flatMap(f -> f.collect(Collectors.joining())) //
				.map(String::trim) //
				.filter(s -> !s.isEmpty()) //
				.map(SyslogPayload::new) //
				.doOnNext(this::handleMessage) //
				.subscribe();
		return Flux.never();
	}

	void handleMessage(SyslogPayload payload) {
		Optional<String> errors = payload.errors();
		if (errors.isPresent()) {
			err.error("error={}, undecoded={}", errors.get(), payload.undecoded());
			return;
		}
		log.info(
				"timestamp:{}\tfacility:{}\tseverity:{}\thost:{}\tapp:{}\tprocId:{}\tmsgId:{}\tstructuredData:{}\tmsg:{}",
				payload.timestamp(), payload.facility(), payload.severityText(),
				payload.host(), payload.appName(), payload.procId(), payload.msgId(),
				payload.structuredData(), payload.message().trim());
	}
}
