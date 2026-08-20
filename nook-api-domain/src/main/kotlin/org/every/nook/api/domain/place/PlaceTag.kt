package org.every.nook.api.domain.place

enum class PlaceTagCategory {
    ATMOSPHERE,
    SPACE,
    PURPOSE,
    EXPERIENCE,
    FOOD_AND_BEVERAGE,
}

data class PlaceTagDefinition(
    val tag: String,
    val category: PlaceTagCategory,
    val displayName: String,
    val matchingKeywords: Set<String>,
    val enabled: Boolean,
    val sortOrder: Int,
) {
    init {
        require(displayName.isNotBlank())
        require(matchingKeywords.none(String::isBlank))
        require(sortOrder > 0)
    }
}

private fun tagKeywords(vararg values: String): Set<String> = values.toSet()

@Suppress("MaxLineLength")
enum class PlaceTag(
    val category: PlaceTagCategory,
    val displayName: String,
    val matchingKeywords: Set<String>,
    val selectable: Boolean = true,
) {
    AESTHETIC(PlaceTagCategory.ATMOSPHERE, "감성적인", tagKeywords("감성", "감성적", "감성적인", "감성 가득", "무드 있는")),
    COZY(PlaceTagCategory.ATMOSPHERE, "아늑한", tagKeywords("아늑", "아늑한", "포근한", "포근", "cozy")),
    QUIET(PlaceTagCategory.ATMOSPHERE, "조용한", tagKeywords("조용", "조용한", "한적한", "고요한", "북적이지 않는")),
    CALM(PlaceTagCategory.ATMOSPHERE, "차분한", tagKeywords("차분", "차분한", "잔잔한", "차분한 분위기")),
    WARM(PlaceTagCategory.ATMOSPHERE, "따뜻한", tagKeywords("따뜻한", "따스한", "온화한", "따뜻한 분위기")),
    NEAT(PlaceTagCategory.ATMOSPHERE, "정갈한", tagKeywords("정갈", "정갈한", "단정한")),
    CLEAN(PlaceTagCategory.ATMOSPHERE, "깔끔한", tagKeywords("깔끔", "깔끔한", "깨끗한", "군더더기 없는")),
    SOPHISTICATED(PlaceTagCategory.ATMOSPHERE, "세련된", tagKeywords("세련", "세련된", "모던한", "모던")),
    HIP(PlaceTagCategory.ATMOSPHERE, "힙한", tagKeywords("힙한", "힙하다", "힙한 분위기", "트렌디한")),
    VINTAGE(PlaceTagCategory.ATMOSPHERE, "빈티지한", tagKeywords("빈티지", "빈티지한", "앤틱", "빈티지 무드")),
    RETRO(PlaceTagCategory.ATMOSPHERE, "레트로한", tagKeywords("레트로", "복고", "복고풍", "옛날 감성")),
    EXOTIC(PlaceTagCategory.ATMOSPHERE, "이국적인", tagKeywords("이국적", "이국적인", "해외 느낌", "외국 느낌")),
    LUXURIOUS(PlaceTagCategory.ATMOSPHERE, "고급스러운", tagKeywords("고급스러운", "고급진", "럭셔리", "우아한")),
    CASUAL(PlaceTagCategory.ATMOSPHERE, "캐주얼한", tagKeywords("캐주얼", "편한 분위기", "부담 없는")),
    ROMANTIC(PlaceTagCategory.ATMOSPHERE, "로맨틱한", tagKeywords("로맨틱", "낭만적인", "낭만", "분위기 있는")),
    LIVELY(PlaceTagCategory.ATMOSPHERE, "활기찬", tagKeywords("활기찬", "활기 넘치는", "생기 있는")),
    COMFORTABLE(PlaceTagCategory.ATMOSPHERE, "편안한", tagKeywords("편안한", "편안하게", "편한 공간", "편하게")),
    DISTINCTIVE(PlaceTagCategory.ATMOSPHERE, "독특한", tagKeywords("독특한", "특이한", "개성 있는", "유니크한")),
    NATURAL(PlaceTagCategory.ATMOSPHERE, "자연스러운", tagKeywords("자연스러운", "내추럴한", "내추럴")),
    DREAMY(PlaceTagCategory.ATMOSPHERE, "몽환적인", tagKeywords("몽환적", "몽환적인", "신비로운", "dreamy")),

    COMPACT(PlaceTagCategory.SPACE, "아담한", tagKeywords("아담한", "아담", "작은 공간", "소규모")),
    SPACIOUS(PlaceTagCategory.SPACE, "넓은", tagKeywords("넓은", "널찍한", "넓고", "넓어서")),
    PLEASANT(PlaceTagCategory.SPACE, "쾌적한", tagKeywords("쾌적", "쾌적한", "답답하지 않은")),
    OPEN(PlaceTagCategory.SPACE, "개방적인", tagKeywords("개방감", "개방적인", "탁 트인", "트여 있는")),
    PRIVATE(PlaceTagCategory.SPACE, "프라이빗한", tagKeywords("프라이빗", "독립된 공간", "개별 공간", "룸")),
    GOOD_LIGHTING(PlaceTagCategory.SPACE, "채광 좋은", tagKeywords("채광", "자연광", "햇살 맛집", "햇빛 잘 드는")),
    GOOD_VIEW(PlaceTagCategory.SPACE, "뷰 좋은", tagKeywords("뷰맛집", "전망 좋은", "경치 좋은", "뷰가 좋은")),
    FULL_WINDOW(PlaceTagCategory.SPACE, "통창", tagKeywords("통창", "큰 창", "전면창", "창이 큰")),
    WINDOW_SEAT(PlaceTagCategory.SPACE, "창가", tagKeywords("창가", "창가석", "창가 자리")),
    TERRACE(PlaceTagCategory.SPACE, "테라스", tagKeywords("테라스", "테라스석", "야외 테라스")),
    ROOFTOP(PlaceTagCategory.SPACE, "루프탑", tagKeywords("루프탑", "옥상", "옥상 테라스")),
    OUTDOOR(PlaceTagCategory.SPACE, "야외", tagKeywords("야외", "야외석", "야외 자리", "외부 좌석")),
    GARDEN(PlaceTagCategory.SPACE, "정원", tagKeywords("정원", "가든", "마당", "야외 정원")),
    HANOK(PlaceTagCategory.SPACE, "한옥", tagKeywords("한옥", "한옥 공간", "한옥 카페", "고택")),
    OCEAN_VIEW(PlaceTagCategory.SPACE, "오션뷰", tagKeywords("오션뷰", "바다뷰", "바다 보이는", "바다 전망")),
    RIVER_VIEW(PlaceTagCategory.SPACE, "리버뷰", tagKeywords("리버뷰", "한강뷰", "강뷰", "강이 보이는")),
    CITY_VIEW(PlaceTagCategory.SPACE, "시티뷰", tagKeywords("시티뷰", "도심뷰", "도시 전망", "야경")),
    FOREST_VIEW(PlaceTagCategory.SPACE, "숲뷰", tagKeywords("숲뷰", "숲속", "숲이 보이는", "나무뷰")),
    INTERIOR(PlaceTagCategory.SPACE, "인테리어", tagKeywords("인테리어", "공간 디자인", "내부 인테리어")),
    PHOTO_ZONE(PlaceTagCategory.SPACE, "포토존", tagKeywords("포토존", "인증샷", "사진 스팟", "포토스팟")),

    DATE(PlaceTagCategory.PURPOSE, "데이트", tagKeywords("데이트", "데이트코스", "커플", "데이트하기 좋은")),
    GROUP(PlaceTagCategory.PURPOSE, "모임", tagKeywords("모임", "단체", "여러 명", "친구들과")),
    BLIND_DATE(PlaceTagCategory.PURPOSE, "소개팅", tagKeywords("소개팅", "첫 만남", "소개팅 장소")),
    SOLO(PlaceTagCategory.PURPOSE, "혼자", tagKeywords("혼자", "혼자 가기 좋은", "혼자서")),
    SOLO_DINING(PlaceTagCategory.PURPOSE, "혼밥", tagKeywords("혼밥", "혼자 밥", "혼자 먹기 좋은")),
    SOLO_CAFE(PlaceTagCategory.PURPOSE, "혼카페", tagKeywords("혼카페", "혼자 카페", "혼자 가기 좋은 카페")),
    WORK(PlaceTagCategory.PURPOSE, "작업", tagKeywords("작업", "작업하기 좋은", "작업 카페", "노트북")),
    STUDY(PlaceTagCategory.PURPOSE, "공부", tagKeywords("공부", "공부하기 좋은", "스터디", "공부 카페")),
    GOOD_FOR_CONVERSATION(PlaceTagCategory.PURPOSE, "대화하기 좋은", tagKeywords("대화하기 좋은", "이야기하기 좋은", "얘기하기 좋은")),
    READING(PlaceTagCategory.PURPOSE, "독서", tagKeywords("독서", "책 읽기 좋은", "책 읽기", "북카페")),
    ANNIVERSARY(PlaceTagCategory.PURPOSE, "기념일", tagKeywords("기념일", "특별한 날", "anniversary")),
    BIRTHDAY(PlaceTagCategory.PURPOSE, "생일", tagKeywords("생일", "생일파티", "생일 모임", "birthday")),
    FAMILY(PlaceTagCategory.PURPOSE, "가족모임", tagKeywords("가족 모임", "가족과", "부모님과", "가족 식사")),
    COMPANY_DINNER(PlaceTagCategory.PURPOSE, "회식", tagKeywords("회식", "단체 회식", "직장 모임")),
    FRIEND_GATHERING(PlaceTagCategory.PURPOSE, "친구모임", tagKeywords("친구 모임", "친구들과", "친구랑", "친구와")),
    PET_FRIENDLY(PlaceTagCategory.PURPOSE, "반려동물", tagKeywords("애견동반", "반려동물 동반", "강아지 동반", "펫프렌들리")),
    WITH_KIDS(PlaceTagCategory.PURPOSE, "아이와", tagKeywords("아이와", "아이랑", "아기랑", "아이 동반")),
    OUTING(PlaceTagCategory.PURPOSE, "나들이", tagKeywords("나들이", "주말 나들이", "산책 코스", "주말에 가기 좋은")),
    TRAVEL(PlaceTagCategory.PURPOSE, "여행", tagKeywords("여행", "여행 코스", "여행지", "여행 중")),
    DRIVE(PlaceTagCategory.PURPOSE, "드라이브", tagKeywords("드라이브", "드라이브 코스", "차 타고 가기 좋은")),

    HIDDEN_GEM(PlaceTagCategory.EXPERIENCE, "숨은명소", tagKeywords("숨은 명소", "숨겨진", "아는 사람만", "잘 알려지지 않은")),
    HOT_PLACE(PlaceTagCategory.EXPERIENCE, "핫플", tagKeywords("핫플", "핫플레이스", "요즘 핫한", "인기 많은")),
    NEW_OPENING(PlaceTagCategory.EXPERIENCE, "신상", tagKeywords("신상", "새로 생긴", "신규 오픈", "새로 오픈")),
    LOCAL(PlaceTagCategory.EXPERIENCE, "로컬", tagKeywords("로컬", "현지인 맛집", "동네 맛집", "현지인 추천")),
    WAITING(PlaceTagCategory.EXPERIENCE, "웨이팅", tagKeywords("웨이팅", "대기", "줄 서서", "대기시간")),
    RESERVATION_REQUIRED(PlaceTagCategory.EXPERIENCE, "예약필수", tagKeywords("예약 필수", "예약해야", "사전 예약", "예약 추천")),
    RESERVATION_AVAILABLE(PlaceTagCategory.EXPERIENCE, "예약가능", tagKeywords("예약 가능", "예약할 수", "네이버 예약", "캐치테이블")),
    GOOD_VALUE(PlaceTagCategory.EXPERIENCE, "가성비", tagKeywords("가성비", "가격 대비", "합리적인 가격", "가격이 착한")),
    UNIQUE(PlaceTagCategory.EXPERIENCE, "특별한", tagKeywords("특별한", "색다른", "이색적인", "이색")),
    PHOTOGENIC(PlaceTagCategory.EXPERIENCE, "사진찍기 좋은", tagKeywords("사진 찍기 좋은", "사진 맛집", "인생샷", "사진 잘 나오는")),
    REVISIT(PlaceTagCategory.EXPERIENCE, "재방문", tagKeywords("재방문", "또 가고 싶은", "다시 가고 싶은", "또 갈")),
    LONG_ESTABLISHED(PlaceTagCategory.EXPERIENCE, "오래된", tagKeywords("오래된", "오래된 가게", "오래된 곳", "오랜 역사의")),
    OLD_SCHOOL(PlaceTagCategory.EXPERIENCE, "노포", tagKeywords("노포", "오래된 맛집", "노포 맛집", "전통 있는")),
    FAMOUS(PlaceTagCategory.EXPERIENCE, "유명한", tagKeywords("유명한", "유명 맛집", "소문난", "잘 알려진")),
    FRIENDLY(PlaceTagCategory.EXPERIENCE, "친절한", tagKeywords("친절한", "친절하시고", "친절해서", "서비스 친절")),
    RELAXED(PlaceTagCategory.EXPERIENCE, "여유로운", tagKeywords("여유로운", "여유 있게", "여유로운 분위기", "느긋한")),
    CROWDED(PlaceTagCategory.EXPERIENCE, "북적이는", tagKeywords("북적이는", "사람 많은", "북적북적", "붐비는")),
    ACCESSIBLE(PlaceTagCategory.EXPERIENCE, "접근성 좋은", tagKeywords("역 근처", "역에서 가까운", "접근성 좋은", "역세권")),
    PARKING(PlaceTagCategory.EXPERIENCE, "주차가능", tagKeywords("주차 가능", "주차장", "무료 주차", "주차할 수")),
    LATE_NIGHT(PlaceTagCategory.EXPERIENCE, "심야", tagKeywords("늦게까지", "늦은 시간", "심야", "밤늦게")),

    BRUNCH(PlaceTagCategory.FOOD_AND_BEVERAGE, "브런치", tagKeywords("브런치", "브런치 맛집", "브런치 카페")),
    BAKERY(PlaceTagCategory.FOOD_AND_BEVERAGE, "베이커리", tagKeywords("베이커리", "빵집", "빵 맛집", "제과")),
    DESSERT(PlaceTagCategory.FOOD_AND_BEVERAGE, "디저트", tagKeywords("디저트", "디저트 맛집", "디저트 카페")),
    COFFEE(PlaceTagCategory.FOOD_AND_BEVERAGE, "커피", tagKeywords("커피 맛집", "원두", "핸드드립", "커피가 맛있는")),
    CAKE(PlaceTagCategory.FOOD_AND_BEVERAGE, "케이크", tagKeywords("케이크", "케이크 맛집", "홀케이크", "조각 케이크")),
    WINE(PlaceTagCategory.FOOD_AND_BEVERAGE, "와인", tagKeywords("와인", "와인바", "내추럴 와인", "와인 페어링")),
    COCKTAIL(PlaceTagCategory.FOOD_AND_BEVERAGE, "칵테일", tagKeywords("칵테일", "칵테일바", "바텐딩")),
    WHISKEY(PlaceTagCategory.FOOD_AND_BEVERAGE, "위스키", tagKeywords("위스키", "위스키바", "몰트", "싱글몰트")),
    TRADITIONAL_LIQUOR(PlaceTagCategory.FOOD_AND_BEVERAGE, "전통주", tagKeywords("전통주", "막걸리", "약주", "우리술")),
    VEGAN(PlaceTagCategory.FOOD_AND_BEVERAGE, "비건", tagKeywords("비건", "vegan", "비건 메뉴", "식물성")),
    VEGETARIAN(PlaceTagCategory.FOOD_AND_BEVERAGE, "채식", tagKeywords("채식", "베지테리언", "채식 메뉴", "vegetarian")),
    FINE_DINING(PlaceTagCategory.FOOD_AND_BEVERAGE, "파인다이닝", tagKeywords("파인다이닝", "fine dining", "파인 다이닝")),
    COURSE_MEAL(PlaceTagCategory.FOOD_AND_BEVERAGE, "코스요리", tagKeywords("코스요리", "코스 메뉴", "코스로", "course")),
    OMAKASE(PlaceTagCategory.FOOD_AND_BEVERAGE, "오마카세", tagKeywords("오마카세", "맡김차림", "스시 오마카세")),
    DINING_BAR(PlaceTagCategory.FOOD_AND_BEVERAGE, "다이닝바", tagKeywords("다이닝바", "다이닝 바", "음식과 술")),
    PUB(PlaceTagCategory.FOOD_AND_BEVERAGE, "펍", tagKeywords("펍", "pub", "생맥주", "맥주집")),
    TEA_ROOM(PlaceTagCategory.FOOD_AND_BEVERAGE, "티룸", tagKeywords("티룸", "티하우스", "차 전문점", "티 코스")),
    GOOD_FOOD(PlaceTagCategory.FOOD_AND_BEVERAGE, "맛집", tagKeywords("맛집", "맛있는 곳", "맛있었던", "맛있어요")),
    BAR(PlaceTagCategory.FOOD_AND_BEVERAGE, "술집", tagKeywords("술집", "한잔하기 좋은", "술 마시기 좋은")),
    CAFE(PlaceTagCategory.FOOD_AND_BEVERAGE, "카페", tagKeywords("카페", "coffee shop", "커피숍", "카페 추천")),

    GENEROUS(PlaceTagCategory.FOOD_AND_BEVERAGE, "푸짐한", emptySet(), selectable = false),
    FAST(PlaceTagCategory.EXPERIENCE, "빠른", emptySet(), selectable = false),
    ;

    companion object {
        val selectableEntries: List<PlaceTag> = entries.filter(PlaceTag::selectable)
        val defaultDefinitions: List<PlaceTagDefinition> = selectableEntries.mapIndexed { index, tag ->
            PlaceTagDefinition(
                tag = tag.name,
                category = tag.category,
                displayName = tag.displayName,
                matchingKeywords = tag.matchingKeywords,
                enabled = true,
                sortOrder = index + 1,
            )
        }
    }
}
