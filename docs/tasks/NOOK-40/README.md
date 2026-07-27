# NOOK-40 S3·CloudFront 게시물 미디어 저장 구현

## 목적

외부 Instagram 미디어 URL을 그대로 저장하는 pass-through 구현을 교체해 외부 URL 수명과 무관하게
게시물 이미지와 영상을 제공한다.

## 범위

- `PostMediaStoragePort`의 S3 adapter
- 외부 미디어의 HTTPS·공인 주소 검증
- Content-Type, 파일 크기, redirect, timeout 검증
- SHA-256 content-addressed object key와 중복 업로드 방지
- 환경별 private S3 bucket과 CloudFront distribution
- CloudFront Origin Access Control과 애플리케이션 최소 IAM 권한
- dev/live 설정 및 배포 절차

## 제외 범위

- 이미지 리사이징과 썸네일 생성
- 영상 transcoding
- CloudFront custom domain, ACM 인증서와 WAF
- CloudFront signed URL과 signed cookie
- 기존 외부 미디어 URL backfill

## 결정

### 공개 범위

미디어는 URL을 아는 사용자가 조회할 수 있는 공개 콘텐츠로 취급한다. S3 bucket은 공개하지 않고
CloudFront OAC에만 읽기 권한을 부여한다. 사용자별 비공개 미디어가 필요해지면 object key 저장과
CloudFront signed URL 발급을 별도 이슈에서 도입한다.

### 환경 분리

dev와 live는 같은 bucket의 디렉터리로 나누지 않는다. 동일한 CloudFormation template을
`nook-dev-media`, `nook-live-media` stack으로 각각 배포해 bucket, distribution과 IAM policy를 격리한다.

### object key와 DB 저장값

다운로드한 byte의 SHA-256과 검증된 Content-Type에서 object key를 생성한다.

```text
post-media/sha256/{digest 앞 2자리}/{digest}.{extension}
```

같은 콘텐츠는 출처 URL이나 query parameter가 달라도 같은 key를 사용한다. DB의 기존 `media_url`에는
`{AWS_CLOUDFRONT_BASE_URL}/{object key}`를 저장하므로 스키마 변경은 없다.

### 실패 정책

미디어를 모두 저장한 다음 게시물 DB transaction을 실행한다. 미디어 하나라도 다운로드 또는 업로드에
실패하면 게시물을 저장하지 않는다. 앞서 업로드한 content-addressed object는 삭제하지 않는다. 같은
콘텐츠를 사용하는 다른 게시물과 경쟁할 수 있고, 재시도 시 같은 object를 재사용하기 때문이다.

## AWS CLI와 인증

AWS CLI v2를 설치하고 SSO 또는 단기 credential을 사용하는 profile로 인증한다. access key를 저장소나
application 설정에 기록하지 않는다.

```shell
aws configure sso --profile nook
aws sso login --profile nook
AWS_PROFILE=nook AWS_PAGER='' aws sts get-caller-identity
```

애플리케이션은 AWS SDK `DefaultCredentialsProvider`를 사용한다. 로컬에서는 `AWS_PROFILE`, 배포
환경에서는 실행 role을 사용한다.

## 인프라 배포

배포 전에 계정과 리전을 확인한다.

```shell
export AWS_PROFILE=nook
export AWS_REGION=ap-northeast-2
AWS_PAGER='' aws sts get-caller-identity
```

dev:

```shell
aws cloudformation deploy \
  --stack-name nook-dev-media \
  --template-file infra/aws/media-storage.yml \
  --parameter-overrides \
    Environment=dev \
    MediaWriterRoleName=nook-dev-media-writer \
  --capabilities CAPABILITY_NAMED_IAM \
  --region "${AWS_REGION}"
```

live:

```shell
aws cloudformation deploy \
  --stack-name nook-live-media \
  --template-file infra/aws/media-storage.yml \
  --parameter-overrides \
    Environment=live \
    MediaWriterRoleName=nook-media-writer \
  --capabilities CAPABILITY_NAMED_IAM \
  --region "${AWS_REGION}"
```

출력값은 다음 명령으로 확인한다.

```shell
aws cloudformation describe-stacks \
  --stack-name nook-dev-media \
  --region "${AWS_REGION}" \
  --query 'Stacks[0].Outputs'
```

CloudFormation은 `MediaWriterRoleName`으로 지정한 기존 role에 환경별 inline policy를 연결한다.
임의의 IAM user나 장기 access key는 만들지 않는다.

### 외부 dev VM 인증

EC2 instance profile을 사용할 수 없는 외부 dev VM은 `nook-dev-vm-bootstrap` IAM user의 access key로
직접 S3에 접근하지 않는다. 이 user에는 `nook-dev-media-writer` role을 AssumeRole하는 권한만 부여하고,
role trust policy는 다음 두 조건을 모두 적용한다.

- principal ARN: `arn:aws:iam::552150274697:user/nook-dev-vm-bootstrap`
- source IP: `1.201.120.75/32`

VM의 `/opt/nook/api/secrets/aws-credentials`에는 bootstrap credential만 저장하고
`/opt/nook/api/secrets/aws-config`에는 `nook-dev-media` role profile을 둔다. 두 파일은 컨테이너의
UID/GID `100:101`만 읽을 수 있도록 `0400`, 상위 디렉터리는 root 소유 `0700`으로 유지한다.
Compose는 다음 경로로 읽기 전용 마운트한다.

```text
/run/secrets/aws_credentials
/run/secrets/aws_config
```

컨테이너 환경에는 다음 값을 추가한다.

```text
AWS_PROFILE=nook-dev-media
AWS_SHARED_CREDENTIALS_FILE=/run/secrets/aws_credentials
AWS_CONFIG_FILE=/run/secrets/aws_config
```

bootstrap access key는 저장소, `.env`, image layer와 로그에 기록하지 않는다. VM 공인 IP가 바뀌면
role trust의 `/32` 조건을 먼저 갱신하며, credential 노출이 의심되면 해당 key를 즉시 비활성화·교체한다.

## 애플리케이션 설정

staging/live 배포 환경에는 다음 값을 설정한다.

```text
MEDIA_STORAGE_ENABLED=true
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET={BucketName output}
AWS_CLOUDFRONT_BASE_URL={CloudFrontBaseUrl output}
```

선택 설정:

```text
MEDIA_DOWNLOAD_CONNECT_TIMEOUT=3s
MEDIA_DOWNLOAD_READ_TIMEOUT=30s
MEDIA_MAX_IMAGE_BYTES=20971520
MEDIA_MAX_VIDEO_BYTES=104857600
MEDIA_MAX_REDIRECTS=3
```

local과 test는 `MEDIA_STORAGE_ENABLED=false`가 기본이며 기존 URL을 통과시킨다.

## 보안

- source URL은 HTTPS만 허용한다.
- DNS 조회 결과에 loopback, link-local, site-local, multicast 또는 IPv6 unique-local 주소가 있으면
  거부한다.
- redirect 대상도 같은 검증을 다시 수행한다.
- S3 Public Access Block과 Bucket Owner Enforced를 사용한다.
- CloudFront는 OAC SigV4로만 S3 object를 읽는다.
- 애플리케이션은 `post-media/*`에 대한 `s3:GetObject`, `s3:PutObject`와 object 존재 여부 확인에 필요한
  prefix 제한 `s3:ListBucket`만 사용한다.

## 검증

```shell
aws cloudformation validate-template \
  --template-body file://infra/aws/media-storage.yml
./gradlew detekt
./gradlew test
./gradlew check
```

배포 후 smoke test:

1. dev API로 이미지 게시물과 영상 게시물을 각각 저장한다.
2. 저장된 CloudFront URL이 `200`과 원본 Content-Type을 반환하는지 확인한다.
3. 같은 미디어를 다시 저장해 S3 object 수가 증가하지 않는지 확인한다.
4. S3 direct URL이 공개되지 않는지 확인한다.
5. timeout, 크기 초과와 지원하지 않는 Content-Type에서 게시물이 DB에 남지 않는지 확인한다.

## 롤백

애플리케이션은 `MEDIA_STORAGE_ENABLED=false`로 pass-through adapter로 돌아갈 수 있다.

CloudFormation stack 삭제 시 bucket은 `Retain`된다. 운영 데이터의 의도치 않은 삭제를 막기 위한
정책이며, 완전 제거가 필요하면 bucket이 비어 있고 보존할 데이터가 없는지 확인한 뒤 별도로 삭제한다.

## DDL

기존 `post_media.media_url VARCHAR(2048)`에 CloudFront URL을 저장하므로 DDL 변경은 없다.
