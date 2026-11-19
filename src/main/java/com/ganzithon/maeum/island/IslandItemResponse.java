package com.ganzithon.maeum.island;

/** 섬 꾸미기 아이템 응답입니다. 그림 URL은 아직 서버 그림이 없어 null로 내려갑니다. */
record IslandItemResponse(
        String itemId,
        String name,
        String category,
        int price,
        String imageUrl,
        String placedImageUrl,
        int placedOrder,
        FloatAt floatAt,
        boolean owned) {
    record FloatAt(double x, double y) {}

    static IslandItemResponse of(IslandItem item, boolean owned) {
        FloatAt floatAt =
                item.getFloatX() == null
                        ? null
                        : new FloatAt(item.getFloatX().doubleValue(), item.getFloatY().doubleValue());
        return new IslandItemResponse(
                item.getId().toString(),
                item.getName(),
                item.getCategory(),
                item.getPrice(),
                null,
                null,
                item.getPlacedOrder(),
                floatAt,
                owned);
    }
}
