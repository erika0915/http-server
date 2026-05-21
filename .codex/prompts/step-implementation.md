# Step Implementation Prompt

아래 템플릿은 Java NIO HTTP 서버의 새로운 step을 진행할 때 사용한다.

프로젝트의 공통 규칙은 `AGENTS.md`를 따른다.

## 사용 예시

```text
.codex/prompts/step-implementation.md 기준으로 Step 14 진행해줘.

Step 번호: 14
Step 이름: HTTP/1.1 Request Validation
목표:
- 잘못된 HTTP Request Line을 감지한다.
- malformed request에 대해 400 Bad Request를 반환한다.

구현 요구사항:
- ...
```

## Prompt Template

```text
Java NIO 기반 HTTP 서버의 Step {STEP_NUMBER}를 구현해줘.

Step 이름:
- {STEP_NAME}

현재 상태:
- {CURRENT_STATE_1}
- {CURRENT_STATE_2}
- {CURRENT_STATE_3}

목표:
- {GOAL_1}
- {GOAL_2}
- {GOAL_3}

구현 요구사항:
1. {IMPLEMENTATION_REQUIREMENT_1}
2. {IMPLEMENTATION_REQUIREMENT_2}
3. {IMPLEMENTATION_REQUIREMENT_3}

학습 요구사항:
- {LEARNING_POINT_1}
- {LEARNING_POINT_2}
- {LEARNING_POINT_3}

문서 요구사항:
- docs/{STEP_NUMBER}-{DOC_SLUG}.md 작성
- README.md 진행 상황 표 업데이트
- 문서에는 목표, 구현 내용, 핵심 개념, 코드 흐름, 실행 방법, 테스트 방법, 완료 기준을 포함

검증 요구사항:
- {VERIFICATION_1}
- {VERIFICATION_2}
- {VERIFICATION_3}

이번 Step에서 하지 말 것:
- {NON_GOAL_1}
- {NON_GOAL_2}
- {NON_GOAL_3}

완료 기준:
- 코드 구현 완료
- docs 작성 완료
- README 표 업데이트 완료
- 최소 1개 이상의 검증 수행
- 검증하지 못한 항목은 이유를 명시
```

## Notes

- Step 하나는 `Code + Docs + README + Verification` 단위로 진행한다.
- README에는 요약만 작성하고, 자세한 설명은 docs에 작성한다.
- 아직 구현하지 않은 기능을 완료된 것처럼 쓰지 않는다.
- benchmark는 HTTP/1.1 기능 완성도를 높인 뒤 별도 step으로 진행한다.
