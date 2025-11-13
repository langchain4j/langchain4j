package dev.langchain4j.internal;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public record Either<L, R>(L left, R right) {

    public static <L, R> Either<L, R> left(L left) {
        return new Either<>(left, null);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Either<>(null, right);
    }

    public boolean isLeft() {
        return left != null;
    }

    public boolean isRight() {
        return right != null;
    }

    public Optional<R> asOptional() {
        return Optional.ofNullable(right);
    }

    public <T> Either<L, T> map(Function<R, T> mapper) {
        return isRight() ? Either.right(mapper.apply(right)) : Either.left(left);
    }

    public <X extends Throwable> R orElseThrow(Function<L, X> exceptionMapper) throws X {
        if (isRight()) {
            return right;
        }
        throw exceptionMapper.apply(left);
    }

    public R orElseGet(Supplier<R> resultSupplier) {
        return isRight() ? right : resultSupplier.get();
    }
}
