# HTTP Step Roadmap Prompt

이 템플릿은 다음 step을 정하기 전에 현재 구현 상태를 보고 로드맵을 조정할 때 사용한다.

프로젝트의 공통 규칙은 `AGENTS.md`를 따른다.

## Prompt Template

```text
현재 Java NIO HTTP 서버의 구현 상태를 기준으로 다음 step 로드맵을 정리해줘.

목표:
- 참고 repo에 가까워지기 위한 다음 구현 순서를 정한다.
- benchmark보다 HTTP/1.1 기능 완성도를 우선한다.
- 각 step은 Code + Docs + README + Verification 단위로 진행 가능해야 한다.

확인할 항목:
1. 현재 구현된 기능
2. 아직 구현되지 않은 HTTP/1.1 기능
3. 다음 step으로 적절한 작업
4. 너무 큰 step은 더 작은 step으로 분리
5. 각 step의 학습 목표
6. 각 step의 검증 방법

출력 형식:
- 번호
- 이름
- 학습 목표
- 구현 요약
- 완료 기준
```
