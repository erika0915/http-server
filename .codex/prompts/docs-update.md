# Docs Update Prompt

이 템플릿은 특정 step의 문서를 보강하거나 README 진행 상황 표를 정리할 때 사용한다.

프로젝트의 공통 규칙은 `AGENTS.md`를 따른다.

## Prompt Template

```text
Step {STEP_NUMBER} 문서를 정리해줘.

대상 문서:
- docs/{STEP_NUMBER}-{DOC_SLUG}.md

목표:
- 해당 step에서 무엇을 구현했는지 이해하기 쉽게 정리
- 너무 긴 설명은 줄이고 핵심 개념 중심으로 정리
- README.md 진행 상황 표와 문서 내용이 일치하도록 정리

문서에 포함할 내용:
1. 목표
2. 구현 내용
3. 핵심 개념
4. 코드 흐름
5. 실행 방법
6. 테스트 방법
7. 완료 기준

주의:
- README에는 요약만 남긴다.
- 자세한 설명은 docs에 둔다.
- 아직 구현하지 않은 기능은 완료된 것처럼 쓰지 않는다.
```
