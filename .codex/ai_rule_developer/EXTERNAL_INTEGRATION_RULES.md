# 외부 연동 규칙 - pfm-FE

외부 시스템 연동은 반드시 명시적인 client 또는 adapter 경계 뒤에 둔다.

[대상]
- LLM API
- Lab, simulation, batch, worker server
- Third-party HTTP API 또는 SDK
- 현재 저장소 프로세스 밖의 모든 시스템

[규칙]
- 인증 정보, 계약, timeout, 실패 매핑이 확인되지 않은 연동은 완성 구현으로 작성하지 않는다.
- 계약이 없으면 class/method 경계만 작성하고 본문은 해당 언어의 미구현 표현으로 둔다.
- TODO 주석에는 필요한 입력, 기대 출력, upstream endpoint 또는 SDK method, timeout, retry, error mapping을 명확히 적는다.
- Service는 비즈니스 의도를 드러내는 좁은 메서드로 외부 연동을 호출한다.
- Upstream DTO는 내부 DTO로 변환한 뒤 호출자에게 반환한다.

[TypeScript 뼈대]
```ts
interface ExternalJobRequestDto {
  simulationId: string;
}

interface ExternalJobResponseDto {
  externalJobId: string;
}

export class ExternalJobAdapter {
  async requestExternalJob(
    requestDto: ExternalJobRequestDto,
  ): Promise<ExternalJobResponseDto> {
    // TODO: upstream endpoint, request field, response field, timeout, retry, failure mapping 정의 필요.
    throw new Error('Not implemented: external job contract is not confirmed.');
  }
}
```

[금지]
- 외부 API fake 구현
- upstream 데이터처럼 보이는 임의 샘플 데이터 생성
- controller 또는 UI component에서 외부 HTTP/SDK 직접 호출

[Next.js 연동 메모]
- Server action과 route handler가 외부 호출을 담당하며 client component는 private upstream system을 직접 호출하지 않는다.
- Server-only secret과 public environment variable을 분리한다.
- External data의 cache/revalidate 동작을 문서화한다.
