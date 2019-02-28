<?php
/**
 * Created by PhpStorm.
 * User: karim
 * Date: 2/27/19
 * Time: 8:42 PM
 */

namespace App\Http\View\Composers;
use Illuminate\View\View;
use App\Models\Location;


class LocationComposer
{
    public function compose(View $view)
    {
        $locations = Location::all();
        $view->with(compact('locations'));
    }
}