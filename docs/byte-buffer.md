# ByteBuffer Experiment

`ByteBuffer`의 `position`, `limit`, `capacity`, `flip()`, `clear()`, `compact()`, `rewind()` 동작을 눈으로 확인하기 위한 학습 문서입니다.

이 문서가 설명하는 파일:

```text
src/main/java/dev/httpserver/nio/buffer/ByteBufferExperiment.java
```

이 코드는 네트워크 서버가 아닙니다. `SocketChannel`, `Selector`, `ServerSocketChannel`을 사용하지 않고 `ByteBuffer`만 실험합니다.

## 실행 방법

IntelliJ에서 실행:

1. `ByteBufferExperiment.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽 실행 버튼을 누릅니다.
3. `Run 'ByteBufferExperiment.main()'`을 선택합니다.

Gradle로 실행:

```bash
gradle runByteBufferExperiment
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/dev/httpserver/nio/buffer/ByteBufferExperiment.java
java -cp build/classes/java/main dev.httpserver.nio.buffer.ByteBufferExperiment
```

## 핵심 개념

`capacity`는 버퍼의 전체 크기입니다. `ByteBuffer.allocate(10)`으로 만들면 capacity는 10이고, 버퍼가 살아 있는 동안 바뀌지 않습니다.

`position`은 다음에 읽거나 쓸 위치입니다. `put()`을 하면 쓴 만큼 증가하고, `get()`을 하면 읽은 만큼 증가합니다.

`limit`은 현재 모드에서 읽거나 쓸 수 있는 끝 위치입니다. 쓰기 모드에서는 보통 capacity와 같고, 읽기 모드에서는 실제로 읽어야 하는 데이터의 끝 위치가 됩니다.

## flip()이 필요한 이유

`put()`으로 데이터를 넣은 직후 buffer는 쓰기 모드처럼 사용된 상태입니다.

예를 들어 `"abc"`를 넣으면:

```text
position=3, limit=10, capacity=10
```

이 상태에서 데이터를 읽으려면 0번 위치부터 3번 위치 전까지 읽어야 합니다. 그래서 `flip()`을 호출합니다.

```text
flip() 후: position=0, limit=3, capacity=10
```

즉, `flip()`은 쓰기 후 읽기로 넘어가기 위한 전환입니다.

## clear()는 데이터를 지우는 것이 아니다

`clear()`는 버퍼 안의 바이트를 0으로 지우는 메서드가 아닙니다.

`clear()`는 다음 쓰기를 준비하도록 상태값을 초기화합니다.

```text
position=0, limit=capacity
```

기존 데이터는 메모리에 남아 있을 수 있지만, 다음 `put()`이 0번 위치부터 덮어쓸 수 있게 됩니다.

## compact()와 clear()의 차이

`clear()`는 읽지 않은 데이터가 있어도 보존하지 않습니다. 다음 쓰기를 위해 버퍼 전체를 다시 쓸 수 있는 상태로 만듭니다.

`compact()`는 아직 읽지 않은 데이터를 버퍼 앞쪽으로 옮겨 보존합니다. 그리고 그 뒤에 새 데이터를 이어 쓸 수 있게 합니다.

예를 들어 `abcde`를 넣고 `a`, `b`만 읽었다면 아직 `cde`가 남아 있습니다.

- `clear()`: `cde`를 보존하지 않고 새로 쓸 준비
- `compact()`: `cde`를 앞쪽으로 옮기고 그 뒤에 새 데이터 쓰기

## rewind()는 언제 쓰는가?

`rewind()`는 이미 읽기 모드인 버퍼를 처음부터 다시 읽고 싶을 때 사용합니다.

`flip()`은 쓰기 모드에서 읽기 모드로 전환할 때 쓰고, `rewind()`는 limit은 유지한 채 position만 0으로 되돌립니다.

예를 들어 `"abc"`를 한 번 다 읽은 뒤 같은 데이터를 다시 읽고 싶다면:

```java
buffer.rewind();
```

를 호출하면 됩니다.

## 왜 NIO HTTP 서버에서 ByteBuffer가 중요한가?

NIO 서버에서 네트워크 데이터는 `SocketChannel`을 통해 바이트 단위로 들어옵니다.

HTTP 요청도 처음에는 문자열 객체가 아니라 바이트 흐름입니다. 서버는 이 바이트를 `ByteBuffer`에 담고, 필요한 만큼 읽고, 아직 덜 온 데이터는 보존하고, 다음 네트워크 이벤트에서 이어서 처리해야 합니다.

그래서 `position`, `limit`, `flip()`, `clear()`, `compact()`를 제대로 이해해야 이후 HTTP 요청 파싱, 헤더 읽기, body 처리 같은 작업을 안전하게 구현할 수 있습니다.

## 예시 출력

실제 출력은 다음과 비슷합니다.

```text
========================================
1. put -> flip -> get -> clear
========================================
allocate(10) 직후          | position= 0, limit=10, capacity=10
"abc" put 전              | position= 0, limit=10, capacity=10
"abc" put 후              | position= 3, limit=10, capacity=10
flip() 호출 전             | position= 3, limit=10, capacity=10
flip() 호출 후             | position= 0, limit= 3, capacity=10
첫 번째 get() 전           | position= 0, limit= 3, capacity=10
읽은 문자: a
첫 번째 get() 후           | position= 1, limit= 3, capacity=10
두 번째 get() 전           | position= 1, limit= 3, capacity=10
읽은 문자: b
두 번째 get() 후           | position= 2, limit= 3, capacity=10
clear() 호출 전            | position= 2, limit= 3, capacity=10
clear() 호출 후            | position= 0, limit=10, capacity=10

========================================
2. compact()가 필요한 상황
========================================
"abcde" put 후            | position= 5, limit=10, capacity=10
flip() 후                 | position= 0, limit= 5, capacity=10
읽은 문자: a
읽은 문자: b
두 글자만 읽은 후          | position= 2, limit= 5, capacity=10
compact() 호출 전         | position= 2, limit= 5, capacity=10
compact() 호출 후         | position= 3, limit=10, capacity=10
"XYZ" 추가 put 후         | position= 6, limit=10, capacity=10
다시 flip() 후            | position= 0, limit= 6, capacity=10
남은 데이터 + 새 데이터: cdeXYZ
모두 읽은 후              | position= 6, limit= 6, capacity=10

========================================
3. rewind()가 필요한 상황
========================================
"abc" put 후 flip()       | position= 0, limit= 3, capacity=10
첫 번째 읽기: abc
첫 번째 읽기 후           | position= 3, limit= 3, capacity=10
rewind() 호출 전          | position= 3, limit= 3, capacity=10
rewind() 호출 후          | position= 0, limit= 3, capacity=10
두 번째 읽기: abc
두 번째 읽기 후           | position= 3, limit= 3, capacity=10
```
