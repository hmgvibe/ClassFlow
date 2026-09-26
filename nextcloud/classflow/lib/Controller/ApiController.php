<?php

declare(strict_types=1);

namespace OCA\ClassFlow\Controller;

use OCA\ClassFlow\AppInfo\Application;
use OCA\ClassFlow\Service\ClassFlowService;
use OCP\AppFramework\Http\Attribute\ApiRoute;
use OCP\AppFramework\Http\Attribute\NoAdminRequired;
use OCP\AppFramework\Http\DataResponse;
use OCP\AppFramework\OCSController;
use OCP\IRequest;

final class ApiController extends OCSController {
    public function __construct(
        IRequest $request,
        private ClassFlowService $service,
        private ?string $userId,
    ) {
        parent::__construct(Application::APP_ID, $request);
    }

    #[NoAdminRequired]
    #[ApiRoute(verb: 'GET', url: '/api/v1/capabilities')]
    public function capabilities(): DataResponse {
        return new DataResponse([
            'apiVersion' => 1,
            'appVersion' => '0.1.0',
            'features' => ['courses', 'fixedWeeklyTimetable', 'agenda', 'reminders', 'batchSync'],
        ]);
    }

    #[NoAdminRequired]
    #[ApiRoute(verb: 'GET', url: '/api/v1/state')]
    public function state(): DataResponse {
        return new DataResponse($this->service->state($this->requireUser()));
    }

    #[NoAdminRequired]
    #[ApiRoute(verb: 'POST', url: '/api/v1/sync')]
    public function sync(array $mutations = []): DataResponse {
        return new DataResponse($this->service->sync($this->requireUser(), $mutations));
    }

    private function requireUser(): string {
        if ($this->userId === null || $this->userId === '') {
            throw new \RuntimeException('Authenticated user required');
        }
        return $this->userId;
    }
}

