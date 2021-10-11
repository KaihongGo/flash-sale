package com.actionworks.flashsale.domain.service.impl;

import com.actionworks.flashsale.domain.event.DomainEventPublisher;
import com.actionworks.flashsale.domain.event.FlashItemEvent;
import com.actionworks.flashsale.domain.event.FlashItemEventType;
import com.actionworks.flashsale.domain.exception.DomainException;
import com.actionworks.flashsale.domain.model.PageResult;
import com.actionworks.flashsale.domain.model.PagesQueryCondition;
import com.actionworks.flashsale.domain.model.entity.FlashItem;
import com.actionworks.flashsale.domain.model.enums.FlashItemStatus;
import com.actionworks.flashsale.domain.repository.FlashItemRepository;
import com.actionworks.flashsale.domain.service.FlashItemDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

import static com.actionworks.flashsale.domain.exception.DomainErrorCode.FLASH_ITEM_DOES_NOT_EXIST;
import static com.actionworks.flashsale.domain.exception.DomainErrorCode.ONLINE_FLASH_ITEM_PARAMS_INVALID;
import static com.actionworks.flashsale.domain.exception.DomainErrorCode.PARAMS_INVALID;

@Service
public class FlashItemDomainServiceImpl implements FlashItemDomainService {
    private static final Logger logger = LoggerFactory.getLogger(FlashItemDomainServiceImpl.class);

    @Resource
    private FlashItemRepository flashItemRepository;

    @Resource
    private DomainEventPublisher domainEventPublisher;

    @Override
    public void publishFlashItem(FlashItem flashItem) {
        logger.info("Preparing to publish flash item:{}", flashItem);
        if (flashItem == null || !flashItem.validateParamsForCreate()) {
            throw new DomainException(ONLINE_FLASH_ITEM_PARAMS_INVALID);
        }
        flashItem.setStatus(FlashItemStatus.PUBLISHED.getCode());
        flashItemRepository.save(flashItem);
        logger.info("Flash item was created:{}", flashItem.getId());

        FlashItemEvent flashItemEvent = new FlashItemEvent();
        flashItemEvent.setEventType(FlashItemEventType.PUBLISHED);
        flashItemEvent.setFlashItem(flashItem);
        domainEventPublisher.publish(flashItemEvent);
    }

    @Override
    public void onlineFlashItem(Long itemId) {
        logger.info("Preparing to online flash item:{},{}", itemId);
        if (itemId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashItem> flashItemOptional = flashItemRepository.findById(itemId);
        if (!flashItemOptional.isPresent()) {
            throw new DomainException(FLASH_ITEM_DOES_NOT_EXIST);
        }
        FlashItem flashItem = flashItemOptional.get();
        if (FlashItemStatus.isOnline(flashItem.getStatus())) {
            return;
        }
        flashItem.setStatus(FlashItemStatus.ONLINE.getCode());
        flashItemRepository.save(flashItem);
        logger.info("Flash item was online:{}", itemId);

        FlashItemEvent flashItemPublishEvent = new FlashItemEvent();
        flashItemPublishEvent.setEventType(FlashItemEventType.ONLINE);
        flashItemPublishEvent.setFlashItem(flashItem);
        domainEventPublisher.publish(flashItemPublishEvent);
    }

    @Override
    public void offlineFlashItem(Long itemId) {
        logger.info("Preparing to offline flash item:{},{}", itemId);
        if (itemId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashItem> flashItemOptional = flashItemRepository.findById(itemId);
        if (!flashItemOptional.isPresent()) {
            throw new DomainException(FLASH_ITEM_DOES_NOT_EXIST);
        }
        FlashItem flashItem = flashItemOptional.get();
        if (FlashItemStatus.isOffline(flashItem.getStatus())) {
            return;
        }
        flashItem.setStatus(FlashItemStatus.OFFLINE.getCode());
        flashItemRepository.save(flashItem);
        logger.info("Flash item was offline:{}", itemId);

        FlashItemEvent flashItemEvent = new FlashItemEvent();
        flashItemEvent.setEventType(FlashItemEventType.OFFLINE);
        flashItemEvent.setFlashItem(flashItem);
        domainEventPublisher.publish(flashItemEvent);
    }

    @Override
    public PageResult<FlashItem> getFlashItems(PagesQueryCondition pagesQueryCondition) {
        if (pagesQueryCondition == null) {
            pagesQueryCondition = new PagesQueryCondition();
        }
        List<FlashItem> flashItems = flashItemRepository.findFlashItemsByCondition(pagesQueryCondition.buildParams());
        Integer total = flashItemRepository.countFlashItemsByCondition(pagesQueryCondition);
        logger.info("Get flash items:{}", flashItems.size());
        return PageResult.with(flashItems, total);
    }

    @Override
    public FlashItem getFlashItem(Long itemId) {
        if (itemId == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        Optional<FlashItem> flashItemOptional = flashItemRepository.findById(itemId);
        if (!flashItemOptional.isPresent()) {
            throw new DomainException(FLASH_ITEM_DOES_NOT_EXIST);
        }
        return flashItemOptional.get();
    }

    @Override
    public boolean decreaseItemStock(Long itemId, Integer quantity) {
        if (itemId == null || quantity == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        return flashItemRepository.decreaseItemStock(itemId, quantity);
    }

    @Override
    public boolean increaseItemStock(Long itemId, Integer quantity) {
        if (itemId == null || quantity == null) {
            throw new DomainException(PARAMS_INVALID);
        }
        return flashItemRepository.increaseItemStock(itemId, quantity);
    }

    @Override
    public boolean isAllowPlaceOrderOrNot(Long itemId) {
        Optional<FlashItem> flashItemOptional = flashItemRepository.findById(itemId);
        if (!flashItemOptional.isPresent()) {
            logger.info("isAllowPlaceOrderOrNot|秒杀品不存在:{}", itemId);
            return false;
        }
        FlashItem flashItem = flashItemOptional.get();
        if (!flashItem.isOnline()) {
            logger.info("isAllowPlaceOrderOrNot|秒杀品尚未上线:{}", itemId);
            return false;
        }
        if (!flashItem.isInProgress()) {
            logger.info("isAllowPlaceOrderOrNot|当前非秒杀时段:{}", itemId);
            return false;
        }
        return true;
    }
}