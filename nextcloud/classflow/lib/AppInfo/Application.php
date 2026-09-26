<?php

declare(strict_types=1);

namespace OCA\ClassFlow\AppInfo;

use OCP\AppFramework\App;

final class Application extends App {
    public const APP_ID = 'classflow';

    public function __construct() {
        parent::__construct(self::APP_ID);
    }
}

