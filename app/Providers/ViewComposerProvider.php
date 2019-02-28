<?php

namespace App\Providers;

use App\Http\View\Composers\EventTypeComposer;
use App\Http\View\Composers\LocationComposer;
use Illuminate\Support\Facades\View;
use Illuminate\Support\ServiceProvider;

class ViewComposerProvider extends ServiceProvider
{
    /**
     * Register services.
     *
     * @return void
     */
    public function register()
    {
        //
    }

    /**
     * Bootstrap services.
     *
     * @return void
     */
    public function boot()
    {
        //
        View::composer(['home','events.list','events.create'],LocationComposer::class);
        View::composer(['events.create'],EventTypeComposer::class);
    }
}
