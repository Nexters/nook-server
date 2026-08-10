ALTER TABLE places
    ADD COLUMN city VARCHAR(50) NULL COMMENT '주소에서 추출한 대표 도시명' AFTER address;

UPDATE places
SET city = CASE
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('서울', '서울시', '서울특별시') THEN '서울'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('부산', '부산시', '부산광역시') THEN '부산'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('대구', '대구시', '대구광역시') THEN '대구'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('인천', '인천시', '인천광역시') THEN '인천'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('광주', '광주시', '광주광역시') THEN '광주'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('대전', '대전시', '대전광역시') THEN '대전'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('울산', '울산시', '울산광역시') THEN '울산'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1)
        IN ('세종', '세종시', '세종특별자치시') THEN '세종'
    WHEN SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1) IN (
        '경기', '경기도',
        '강원', '강원도', '강원특별자치도',
        '충북', '충청북도',
        '충남', '충청남도',
        '전북', '전라북도', '전북특별자치도',
        '전남', '전라남도',
        '경북', '경상북도',
        '경남', '경상남도',
        '제주', '제주도', '제주특별자치도'
    ) AND REGEXP_LIKE(
        SUBSTRING_INDEX(
            SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 2),
            ' ',
            -1
        ),
        '^[가-힣]+(시|군)$'
    ) THEN REGEXP_REPLACE(
        SUBSTRING_INDEX(
            SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 2),
            ' ',
            -1
        ),
        '(시|군)$',
        ''
    )
    WHEN REGEXP_LIKE(
        SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1),
        '^[가-힣]+(시|군)$'
    ) THEN REGEXP_REPLACE(
        SUBSTRING_INDEX(REGEXP_REPLACE(TRIM(address), '[[:space:]]+', ' '), ' ', 1),
        '(시|군)$',
        ''
    )
    ELSE NULL
END;
